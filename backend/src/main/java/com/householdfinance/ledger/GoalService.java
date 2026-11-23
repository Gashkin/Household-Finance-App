package com.householdfinance.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.AccessContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reservations are decisions about existing cash, never additional assets or ledger movements. */
@Service
public class GoalService {
  public record Goal(String id,String name,String kind,LocalDate dueDate,int priority,long targetMinor,
      long reservedMinor,long monthlyMinor,String savingsAccountId,String billAccountId,String categoryId,
      boolean estimated,boolean cancelled,String scheduleId) {}
  public record State(long revision,List<Goal> goals,Map<String,String> operations) {}
  public record Change(Long revision,String operationId,String reason,String id,String name,String kind,
      LocalDate dueDate,Integer priority,String targetEuro,String reservedEuro,String monthlyEuro,
      String savingsAccountId,String billAccountId,String categoryId,boolean estimated,boolean cancelled,
      Long commitmentRevision) {}
  public record Row(Goal goal,long gapMinor,long recommendedMonthlyMinor,Long linkedAccountCashMinor,
      Long actualCostMinor,long remainingReservedMinor,long activeMonthlyMinor,boolean settled,boolean linkedBillConflict,List<String> warnings) {}
  public record Workspace(LocalDate today,State state,List<Row> rows,List<LedgerService.AccountView> accounts,
      List<ActivityReviewService.Category> categories,long cashMinor,long reservedMinor,long unreservedMinor,
      long monthlyMinor,long recommendedMonthlyMinor,Long budgetUnallocatedMinor,List<String> warnings,
      long commitmentRevision) {}
  private final LedgerReviewRepository records;
  private final LedgerAuditRepository audits;
  private final LedgerService ledger;
  private final ActivityReviewService review;
  private final BudgetService budgets;
  private final CommitmentService commitments;
  private final ObjectMapper json;
  public GoalService(LedgerReviewRepository r,LedgerAuditRepository a,LedgerService l,ActivityReviewService v,
      BudgetService b,CommitmentService c,ObjectMapper j){records=r;audits=a;ledger=l;review=v;budgets=b;commitments=c;json=j;}
  private LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Dublin"));}
  private void check(boolean b,String m){if(!b)throw new IllegalArgumentException(m);}
  private String text(String s,int max){check(s!=null&&!s.isBlank()&&s.strip().length()<=max,"A bounded name and reason are required");return s.strip();}
  private String uuid(String s){try{return UUID.fromString(s).toString();}catch(Exception e){throw new IllegalArgumentException("A stable UUID is required");}}
  private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException("Cannot encode goals",e);}}
  private String digest(Object o){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(o).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private String stateId(){return UUID.nameUUIDFromBytes((AccessContext.userId()+":goals").getBytes(StandardCharsets.UTF_8)).toString();}
  private State read(){AccessContext.requireRead();var r=records.findById(stateId()).orElse(null);if(r==null)return new State(0,List.of(),Map.of());
    check(AccessContext.owns(r.ownerId)&&r.kind.equals("GOALS"),"Invalid goals ownership");try{return json.readValue(r.payload,State.class);}catch(Exception e){throw new IllegalStateException("Invalid saved goals; restore a verified backup",e);}}
  private long money(String s){long n=LedgerService.money(s);check(n>=0&&n<=1_000_000_000_000L,"Use a nonnegative amount within the planning limit");return n;}
  private String optional(String s){return s==null||s.isBlank()?null:uuid(s);}
  private List<LedgerService.AccountView> cash(){return ledger.position(today()).accounts().stream().filter(a->a.owned()&&a.kind().equals("CASH")).toList();}
  private LedgerService.AccountView account(List<LedgerService.AccountView> as,String id){return as.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose an owned cash account"));}
  private long available(LedgerService.AccountView a){return Math.subtractExact(a.bookedMinor(),a.pendingDebitMinor());}
  private List<CommitmentService.Occurrence> linked(Goal g){
    if(g.scheduleId()==null)return List.of();LocalDate from=g.dueDate().minusDays(124),to=g.dueDate().plusDays(124);if(from.getYear()<2000)from=LocalDate.of(2000,1,1);if(to.getYear()>2200)to=LocalDate.of(2200,12,31);
    return commitments.upcoming(from,to).stream().filter(o->o.scheduleId().equals(g.scheduleId())).toList();
  }
  private boolean paid(List<CommitmentService.Occurrence> matches){return matches.size()==1&&matches.getFirst().state().equals("MATCHED");}
  private boolean sameFacts(Goal g,CommitmentService.Rule r){return r.direction().equals("EXPENSE")&&r.certainty().equals("EXPECTED")&&r.frequency().equals("ONCE")&&r.name().equals(g.name())&&r.anchor().equals(g.dueDate())&&LedgerService.money(r.amountEuro())==g.targetMinor()&&r.accountId().equals(g.billAccountId())&&r.categoryId().equals(g.categoryId())&&r.amountType().equals(g.estimated()?"ESTIMATED":"FIXED")&&r.end()==null&&r.offsetDays()==0&&r.payrollId()==null&&(r.counterpartId()==null||r.counterpartId().isBlank());}
  private boolean conflict(Goal g,CommitmentService.State state,List<CommitmentService.Occurrence> matches){
    if(g.scheduleId()==null)return false;var schedule=state.schedules().stream().filter(s->s.id().equals(g.scheduleId())).findFirst().orElse(null);if(schedule==null)return true;var latest=schedule.versions().getLast();if(!sameFacts(g,latest.rule()))return true;
    if(latest.cancelled()!=g.cancelled()&&!(g.cancelled()&&paid(matches)))return true;
    if(g.cancelled()&&latest.cancelled())return !matches.isEmpty();
    if(matches.size()!=1)return true;var o=matches.getFirst();
    return !o.direction().equals("EXPENSE")||!o.certainty().equals("EXPECTED")||!o.amountType().equals(g.estimated()?"ESTIMATED":"FIXED")||!o.accountId().equals(g.billAccountId())||!o.categoryId().equals(g.categoryId())||!o.name().equals(g.name())||!o.nominalDate().equals(g.dueDate())||!o.state().equals("MATCHED")&&(!o.date().equals(g.dueDate())||o.amountMinor()!=g.targetMinor()||o.suppressed()||o.entryId()!=null);
  }
  private boolean settled(Goal g){var matches=linked(g);return paid(matches)&&!conflict(g,commitments.get().state(),matches);}
  private long reserved(Goal g){return g.cancelled()||settled(g)?0:g.reservedMinor();}
  @Transactional public State change(Change in){
    AccessContext.requireEdit();check(in!=null,"Complete goal required");var old=read();String op=uuid(in.operationId()),hash=digest(in);
    if(old.operations().containsKey(op)){check(old.operations().get(op).equals(hash),"Operation ID already used with different input");return old;}
    check(in.revision()!=null&&in.revision()==old.revision(),"Goals changed; compare the saved values with your retained draft");
    check(old.operations().size()<2000,"Goal history capacity reached");String id=uuid(in.id()),why=text(in.reason(),200);
    var before=old.goals().stream().filter(g->g.id().equals(id)).findFirst().orElse(null);
    check(before!=null||old.goals().size()<100,"Goal capacity reached");
    String name=text(in.name(),100),kind=Objects.toString(in.kind(),"");check(Set.of("SAVINGS","BILL").contains(kind),"Choose savings or expected bill");
    check(before==null||before.kind().equals(kind),"Retain the goal type; cancel and create a separate goal to change it");
    check(in.dueDate()!=null&&in.dueDate().getYear()>=2000&&in.dueDate().getYear()<=2200,"Choose a supported target date");
    check(in.priority()!=null&&in.priority()>=1&&in.priority()<=5,"Priority must be 1 to 5");
    long target=money(in.targetEuro()),reserved=money(in.reservedEuro()),monthly=money(in.monthlyEuro());
    check(target>0&&reserved<=target,"Use a positive target and reserve no more than the target");
    check(!in.cancelled()||before!=null,"Cannot cancel an unknown goal");
    if(in.cancelled()){reserved=0;monthly=0;}
    var as=cash();String savings=optional(in.savingsAccountId()),bill=optional(in.billAccountId()),category=optional(in.categoryId());
    var lockedAccounts=new HashSet<String>();if(savings!=null)lockedAccounts.add(savings);if(bill!=null)lockedAccounts.add(bill);
    if(before!=null){if(before.savingsAccountId()!=null)lockedAccounts.add(before.savingsAccountId());if(before.billAccountId()!=null)lockedAccounts.add(before.billAccountId());}
    ledger.lockAccountReferences(lockedAccounts);
    if(savings!=null)account(as,savings);
    String schedule=before==null?null:before.scheduleId();var commitment=commitments.get().state();
    if(kind.equals("BILL")){
      check(bill!=null&&category!=null,"Choose the bill payment account and category");account(as,bill);
      check(review.categories().stream().anyMatch(c->c.id().equals(category)&&(!c.retired()||before!=null&&Objects.equals(before.categoryId(),category))),"Choose an active owned category; an existing retired category may be retained");
      // A single existing commitment is the sole cash expectation, including actual matching.
      var existing=schedule==null?null:commitment.schedules().stream().filter(s->s.id().equals(before.scheduleId())).findFirst().orElseThrow(()->new IllegalArgumentException("Linked bill missing; restore verified goals and commitments together"));
      if(before!=null&&in.cancelled())check(before.name().equals(name)&&before.targetMinor()==target&&before.dueDate().equals(in.dueDate())&&Objects.equals(before.billAccountId(),bill)&&Objects.equals(before.categoryId(),category)&&before.estimated()==in.estimated(),"Save bill detail changes before cancelling; cancellation retains the recorded bill facts");
      boolean costChanged=before==null||before.cancelled()!=in.cancelled()||!before.name().equals(name)||before.targetMinor()!=target||!before.dueDate().equals(in.dueDate())||!Objects.equals(before.billAccountId(),bill)||!Objects.equals(before.categoryId(),category)||before.estimated()!=in.estimated();
      boolean closePaid=before!=null&&in.cancelled()&&!before.cancelled()&&settled(before)&&before.name().equals(name)&&before.targetMinor()==target&&before.dueDate().equals(in.dueDate())&&Objects.equals(before.billAccountId(),bill)&&Objects.equals(before.categoryId(),category)&&before.estimated()==in.estimated();
      if(costChanged&&!closePaid){
        check(in.commitmentRevision()!=null&&in.commitmentRevision()==commitment.revision(),"Bills changed; reload before changing a linked cost");
        check(before==null||!before.dueDate().isBefore(today()),"Past bill facts are retained; create a new goal for the next bill");
        check(in.cancelled()||!in.dueDate().isBefore(today()),"Expected bills require a present or future due date");
        if(existing!=null){var v=existing.versions().getLast();var r=v.rule();
          check(v.cancelled()==before.cancelled()&&sameFacts(before,r),"Linked bill changed in Plan ahead; retain it and reconcile before editing the goal");
          check(commitment.exceptions().stream().noneMatch(e->e.id().startsWith(existing.id()+"/")),"Matched or overridden bill facts cannot be rewritten from a goal");
          var occurrence=commitments.upcoming(before.dueDate(),before.dueDate()).stream().filter(o->o.scheduleId().equals(existing.id())).findFirst().orElse(null);
          check(occurrence==null||(!occurrence.suppressed()&&occurrence.entryId()==null),"Matched or overridden bill facts cannot be rewritten from a goal");}
        schedule=schedule==null?id:schedule;
        var rule=new CommitmentService.Rule(text(in.name(),100),bill,category,"EXPENSE","EXPECTED",in.estimated()?"ESTIMATED":"FIXED",in.targetEuro(),"ONCE",in.dueDate(),null,null,0,null);
        commitments.batch(new CommitmentService.Batch(commitment.revision(),op,why,List.of(new CommitmentService.Edit(schedule,existing==null?0L:existing.revision(),today(),in.cancelled(),rule))));
      }
    }else check(bill==null&&category==null,"Savings goals do not create expense expectations");
    var goal=new Goal(id,text(in.name(),100),kind,in.dueDate(),in.priority(),target,reserved,monthly,savings,bill,category,in.estimated(),in.cancelled(),schedule);
    var goals=new ArrayList<>(old.goals());goals.removeIf(g->g.id().equals(id));goals.add(goal);
    // Reduced/released reservations remain possible after a balance drop; increases require backing.
    long previous=old.goals().stream().mapToLong(this::reserved).sum(),total=goals.stream().mapToLong(this::reserved).sum();
    long cash=as.stream().mapToLong(this::available).sum();
    if(total>previous)check(total<=Math.max(0,cash),"Reservations exceed owned cash after pending debits");
    for(var a:as){long was=old.goals().stream().filter(g->a.id().equals(g.savingsAccountId())).mapToLong(this::reserved).sum();long now=goals.stream().filter(g->a.id().equals(g.savingsAccountId())).mapToLong(this::reserved).sum();if(now>was)check(now<=Math.max(0,available(a)),"Linked savings account is already reserved by competing goals");}
    var ops=new LinkedHashMap<>(old.operations());ops.put(op,hash);var next=new State(old.revision()+1,List.copyOf(goals),Map.copyOf(ops));
    var record=new LedgerReviewRecord();record.id=stateId();record.ownerId=AccessContext.userId();record.kind="GOALS";record.revision=next.revision();record.payload=encode(next);records.save(record);
    var audit=new LedgerAudit();audit.id=UUID.randomUUID().toString();audit.accountId=AccessContext.publicationId();audit.action="GOAL_CHANGE";audit.source=why;audit.recordedAt=Instant.now();audit.beforeJson=encode(before);audit.afterJson=encode(goal);audits.save(audit);return next;
  }
  @Transactional(readOnly=true) public Workspace get(){
    var state=read();var as=cash();var warnings=new ArrayList<String>();var rows=new ArrayList<Row>();long reserved=0,monthly=0,recommended=0,cash=as.stream().mapToLong(this::available).sum();
    var commitmentState=commitments.get().state();var effectiveReserves=new HashMap<String,Long>();var linkedById=new HashMap<String,List<CommitmentService.Occurrence>>();var conflicts=new HashMap<String,Boolean>();for(var g:state.goals()){var matches=linked(g);linkedById.put(g.id(),matches);boolean conflict=conflict(g,commitmentState,matches);conflicts.put(g.id(),conflict);effectiveReserves.put(g.id(),g.cancelled()||paid(matches)&&!conflict?0:g.reservedMinor());}
    for(var g:state.goals()){
      var matches=linkedById.get(g.id());boolean conflict=conflicts.get(g.id()),paid=paid(matches)&&!conflict;long remaining=effectiveReserves.get(g.id()),activeMonthly=g.cancelled()||paid?0:g.monthlyMinor();
      var notes=new ArrayList<String>();long gap=g.cancelled()||paid?0:Math.max(0,g.targetMinor()-remaining);
      long months=Math.max(1,ChronoUnit.MONTHS.between(YearMonth.from(today()),YearMonth.from(g.dueDate()))+1),need=(gap+months-1)/months;
      Long linked=null,actual=null;if(g.savingsAccountId()!=null){linked=available(account(as,g.savingsAccountId()));if(state.goals().stream().filter(x->g.savingsAccountId().equals(x.savingsAccountId())).mapToLong(x->effectiveReserves.get(x.id())).sum()>linked)notes.add("Linked account no longer backs its reservations. Release or reallocate explicitly.");}
      if(paid)notes.add("Approved payment matched: no remaining reservation, funding gap or contribution. The original target and reservation decision remain recorded; plan the next bill separately.");
      if(conflict){notes.add("Linked bill conflict: its direction, account, category, recurrence, amount, date, estimate or occurrence state differs. Review Plan ahead; these retained goal targets are not trusted bill/outlook figures.");if(!g.cancelled())warnings.add(g.name()+": linked bill conflict; funding figures are illustrative until the bill and goal are explicitly reconciled.");}
      if(!g.cancelled()&&!paid&&g.dueDate().isBefore(today()))notes.add("Target date passed; review funding and actual evidence.");
      if(!g.cancelled()&&g.monthlyMinor()<need)notes.add("Configured monthly contribution is below the amount needed by the target date.");
      if(g.scheduleId()!=null){
        if(matches.size()==1){var o=matches.getFirst();actual=o.actualMinor();notes.addAll(o.warnings());if(o.amountMinor()!=g.targetMinor()||!o.date().equals(g.dueDate())||!o.name().equals(g.name()))notes.add("Linked bill differs from the goal; review Plan ahead before relying on this target.");}
        else if(!g.cancelled())notes.add("Linked bill date or cancellation changed; review Plan ahead.");}
      rows.add(new Row(g,gap,need,linked,actual,remaining,activeMonthly,paid,conflict,List.copyOf(notes)));if(!g.cancelled()){reserved+=remaining;monthly+=activeMonthly;recommended+=need;}
    }
    rows.sort(Comparator.comparingInt((Row r)->r.goal().priority()).thenComparing(r->r.goal().dueDate()).thenComparing(r->r.goal().name()));
    Long budget=null;var periods=budgets.periods().stream().filter(p->!today().isBefore(p.start())&&today().isBefore(p.end())).toList();if(periods.size()==1)budget=budgets.report(periods.getFirst().id()).unallocatedMinor();
    if(budget!=null&&monthly>budget)warnings.add("Competing monthly contributions exceed the current budget's unallocated plan. Deliberately reallocate or reduce goals; no budget has changed.");
    if(reserved>cash)warnings.add("Current cash no longer backs all reservations. Reduce or release reservations explicitly.");
    if(as.isEmpty()||as.stream().anyMatch(a->!a.reconciled()))warnings.add("Illustrative funding: review and reconcile current account evidence before relying on these amounts.");
    warnings.add("Reservations earmark existing cash. They are neither extra saved assets nor transfers. Contributions are plans, never automatic movements. Expected bills appear once in Plan ahead; match approved actuals there. Budgets remain unchanged.");
    return new Workspace(today(),state,List.copyOf(rows),as,review.categories(),cash,reserved,cash-reserved,monthly,recommended,budget,List.copyOf(warnings),commitments.get().state().revision());
  }
  @Transactional(readOnly=true) public Map<String,Boolean> operation(String id){return Map.of("completed",read().operations().containsKey(uuid(id)));}
  @Transactional(readOnly=true) public List<LedgerAudit> history(){AccessContext.requireRead();return audits.findAll().stream().filter(a->a.actorId.equals(AccessContext.userId())&&a.action.equals("GOAL_CHANGE")).sorted(Comparator.comparing(a->a.recordedAt)).toList();}
}
