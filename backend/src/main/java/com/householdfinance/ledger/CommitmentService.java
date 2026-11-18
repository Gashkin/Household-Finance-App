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

/** Durable expectations, never fabricated ledger activity or allocation movements. */
@Service
public class CommitmentService {
  // 62-day income override plus the difference between two +/-31-day offsets.
  private static final int MAX_RESOLVED_SHIFT_DAYS=124;
  public record Rule(String name,String accountId,String categoryId,String direction,String certainty,
      String amountType,String amountEuro,String frequency,LocalDate anchor,LocalDate end,
      String payrollId,int offsetDays,String counterpartId) {}
  public record Version(LocalDate from,boolean cancelled,Rule rule) {}
  public record Schedule(String id,long revision,List<Version> versions) {}
  public record Edit(String id,Long revision,LocalDate effectiveFrom,boolean cancelled,Rule rule) {}
  public record Batch(Long revision,String operationId,String reason,List<Edit> edits) {}
  public record PayrollRule(String name,int day,List<Integer> weekend,String adjustment,
      List<LocalDate> holidays,Map<LocalDate,LocalDate> exceptions) {}
  public record PayrollVersion(LocalDate from,PayrollRule rule) {}
  public record Payroll(String id,long revision,List<PayrollVersion> versions) {}
  public record PayrollEdit(Long revision,String operationId,String reason,String id,Long payrollRevision,
      LocalDate effectiveFrom,PayrollRule rule) {}
  public record ExceptionInput(Long revision,String operationId,String reason,String occurrenceId,
      LocalDate date,String amountEuro,boolean skip,String entryId,String action,Long accountRevision) {}
  public record OccurrenceEdit(String id,LocalDate date,Long amountMinor,boolean skip,String entryId,
      String fingerprint,String action,Rule frozenRule) {}
  public record State(long revision,List<Schedule> schedules,List<Payroll> payrolls,
      List<OccurrenceEdit> exceptions,Map<String,String> operations) {}
  public record Occurrence(String id,String scheduleId,LocalDate nominalDate,LocalDate date,String name,
      String accountId,String categoryId,String direction,String certainty,String amountType,long amountMinor,
      String counterpartId,String state,String entryId,Long actualMinor,boolean suppressed,List<String> warnings) {}
  public record Candidate(String id,String accountId,LocalDate date,String description,long amountMinor,
      String status,long accountRevision) {}
  public record Workspace(LocalDate today,State state,List<LedgerService.AccountView> accounts,
      List<ActivityReviewService.Category> categories) {}
  public record CsvInput(String csv) {}
  public record CsvRow(int row,Edit edit,List<String> errors) {}
  public record CsvPreview(long revision,List<CsvRow> rows,boolean applicable) {}
  public record CsvApply(Long revision,String operationId,String reason,String csv) {}
  public record BatchPreview(long revision,List<Occurrence> before,List<Occurrence> after) {}
  public record ComparisonInput(Long revision,LocalDate baseline,LocalDate end,boolean uncertain,
      boolean cashOnly,String sourceAccount,String destinationAccount,LocalDate transferDate,String transferEuro,
      String delayedIncomeId,LocalDate delayedUntil) {}
  public record Comparison(Outlook base,Outlook scenario) {}
  public record Point(LocalDate date,long openingMinor,long expensesMinor,long incomeMinor,long lowMinor,long closingMinor,List<String> occurrences) {}
  public record CashLine(String id,String name,long startingMinor,long lowestMinor,LocalDate lowestDate,
      long closingMinor,LocalDate shortfallDate,List<Point> days) {}
  public record Outlook(LocalDate baseline,LocalDate end,boolean currentEvidence,List<String> warnings,
      List<Occurrence> occurrences,CashLine household,List<CashLine> accounts) {}
  private final LedgerReviewRepository records;
  private final LedgerAccountRepository accounts;
  private final LedgerEntryRepository entries;
  private final LedgerAuditRepository audits;
  private final LedgerService ledger;
  private final ActivityReviewService review;
  private final ObjectMapper json;
  public CommitmentService(LedgerReviewRepository r,LedgerAccountRepository a,LedgerEntryRepository e,
      LedgerAuditRepository h,LedgerService l,ActivityReviewService v,ObjectMapper j) {
    records=r;accounts=a;entries=e;audits=h;ledger=l;review=v;json=j;
  }
  private LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Dublin"));}
  private void check(boolean b,String m){if(!b)throw new IllegalArgumentException(m);}
  private String bounded(String s,int max){check(s!=null&&!s.isBlank()&&s.strip().length()<=max,"A bounded name or reason is required");return s.strip();}
  private String uuid(String s){try{return UUID.fromString(s).toString();}catch(Exception e){throw new IllegalArgumentException("A stable UUID is required");}}
  private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException("Cannot encode commitments",e);}}
  private String hash(Object o){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(o).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private String stateId(){return UUID.nameUUIDFromBytes((AccessContext.userId()+":commitments").getBytes(StandardCharsets.UTF_8)).toString();}
  private State read(){
    AccessContext.requireRead();var r=records.findById(stateId()).orElse(null);
    if(r==null)return new State(0,List.of(),List.of(),List.of(),Map.of());
    check(AccessContext.owns(r.ownerId)&&r.kind.equals("COMMITMENTS"),"Invalid commitments ownership");
    try{return json.readValue(r.payload,State.class);}catch(Exception e){throw new IllegalStateException("Invalid commitments; restore a verified backup",e);}
  }
  private boolean replay(State s,Long revision,String operation,Object request){
    AccessContext.requireEdit();String id=uuid(operation),digest=hash(request);
    if(s.operations().containsKey(id)){check(s.operations().get(id).equals(digest),"Operation ID was already used with different input");return true;}
    check(revision!=null&&revision==s.revision(),"Commitments changed; compare saved and draft before reapplying");
    check(s.operations().size()<2000,"Operation history limit reached; export and retain this plan before further changes");return false;
  }
  private State save(State old,List<Schedule> schedules,List<Payroll> payrolls,List<OccurrenceEdit> exceptions,
      String operation,String reason,Object request){
    String why=bounded(reason,200);check(schedules.size()<=100&&payrolls.size()<=20&&exceptions.size()<=1000,"Commitment capacity reached");
    var operations=new LinkedHashMap<>(old.operations());operations.put(uuid(operation),hash(request));
    var next=new State(old.revision()+1,List.copyOf(schedules),List.copyOf(payrolls),List.copyOf(exceptions),Map.copyOf(operations));
    // Validate linked payroll effects before the first durable write, including conflicting delays.
    for(var edit:exceptions)if(edit.frozenRule()!=null&&"INCOME_LINKED".equals(edit.frozenRule().frequency()))payDate(next,edit.frozenRule().payrollId(),LocalDate.parse(edit.id().substring(edit.id().indexOf('/')+1)));
    occurrences(next,today(),today().plusMonths(12),today());
    if(!old.payrolls().equals(next.payrolls())&&!old.schedules().isEmpty()){
      LocalDate start=resolvedStart(old,today());
      if(start.isBefore(today()))check(occurrences(old,start,today().minusDays(1),today()).equals(occurrences(next,start,today().minusDays(1),today())),"Payroll change would rewrite past obligations; retain the historical calendar and use a future effective date");
    }
    var r=records.findById(stateId()).orElseGet(LedgerReviewRecord::new);r.id=stateId();r.ownerId=AccessContext.userId();r.kind="COMMITMENTS";r.revision=next.revision();r.payload=encode(next);records.save(r);
    var audit=new LedgerAudit();audit.id=UUID.randomUUID().toString();audit.accountId=AccessContext.publicationId();audit.action="COMMITMENT_CHANGE";audit.source=why;audit.recordedAt=Instant.now();audit.beforeJson=encode(old);audit.afterJson=encode(next);audits.save(audit);return next;
  }
  private LedgerAccount account(String id){return accounts.findById(id).filter(a->AccessContext.owns(a.ownerId)&&a.kind.equals("CASH")).orElseThrow(()->new IllegalArgumentException("Choose an owned cash account"));}
  private void date(LocalDate d){check(d!=null&&d.getYear()>=2000&&d.getYear()<=2200,"Choose a date from 2000 to 2200");}
  private long money(String s){long n=LedgerService.money(s);check(n>0&&n<=1_000_000_000_000L,"Use a positive amount within the planning limit");return n;}
  private Rule validate(Rule r,State s){
    check(r!=null,"Complete schedule required");bounded(r.name(),100);account(r.accountId());
    check(review.categories().stream().anyMatch(c->c.id().equals(r.categoryId())&&!c.retired()),"Choose an active owned category");
    check(Set.of("EXPENSE","INCOME","TRANSFER").contains(Objects.toString(r.direction(),"")),"Choose expense, income or planned transfer");
    check(Set.of("EXPECTED","UNCERTAIN").contains(Objects.toString(r.certainty(),""))&&("INCOME".equals(r.direction())||"EXPECTED".equals(r.certainty())),"Only income may be uncertain");
    check(Set.of("FIXED","ESTIMATED").contains(Objects.toString(r.amountType(),"")),"Choose fixed or estimated amount");money(r.amountEuro());
    check(Set.of("ONCE","WEEKLY","FORTNIGHTLY","MONTHLY","INCOME_LINKED").contains(Objects.toString(r.frequency(),"")),"Choose a supported recurrence");date(r.anchor());if(r.end()!=null){date(r.end());check(!r.end().isBefore(r.anchor()),"End must follow the first due date");}
    check(r.offsetDays()>=-31&&r.offsetDays()<=31,"Payroll offset must be between -31 and 31 days");
    if(r.frequency().equals("INCOME_LINKED")){check(s.payrolls().stream().anyMatch(p->p.id().equals(r.payrollId())),"Choose a payroll calendar");payDate(s,r.payrollId(),r.anchor());}
    if(r.direction().equals("TRANSFER")&&r.counterpartId()!=null&&!r.counterpartId().isBlank()){account(r.counterpartId());check(!r.accountId().equals(r.counterpartId()),"Transfer accounts must differ");}
    return r;
  }
  private List<Schedule> edited(State s,List<Edit> edits){
    visible(s);check(edits!=null&&!edits.isEmpty()&&edits.size()<=100,"Provide 1 to 100 changed rows");
    var result=new ArrayList<>(s.schedules());var ids=new HashSet<String>();
    for(var e:edits){check(e!=null,"Complete edit required");String id=uuid(e.id());check(ids.add(id),"Duplicate schedule ID in batch");date(e.effectiveFrom());
      var old=result.stream().filter(x->x.id().equals(id)).findFirst().orElse(null);
      check(e.revision()!=null&&e.revision()==(old==null?0:old.revision()),"Schedule changed; reload saved revisions");
      var versions=new ArrayList<Version>();if(old!=null){versions.addAll(old.versions());check(!e.effectiveFrom().isBefore(today()),"Past schedule versions cannot be rewritten");check(!e.effectiveFrom().isBefore(versions.getLast().from()),"Apply after the last scheduled change");}
      check(!e.cancelled()||old!=null,"Cannot cancel an unknown schedule");
      Rule rule=e.cancelled()?versions.getLast().rule():validate(e.rule(),s);
      check(old!=null||!e.effectiveFrom().isAfter(rule.anchor()),"Initial effective date must cover the first due date");
      check(versions.size()<100,"Schedule history limit reached");versions.add(new Version(e.effectiveFrom(),e.cancelled(),rule));
      if(old!=null)result.remove(old);result.add(new Schedule(id,old==null?1:old.revision()+1,List.copyOf(versions)));
    }
    return List.copyOf(result);
  }
  @Transactional public State batch(Batch b){check(b!=null,"Batch required");var s=visible(read());if(replay(s,b.revision(),b.operationId(),b))return s;return save(s,edited(s,b.edits()),s.payrolls(),s.exceptions(),b.operationId(),b.reason(),b);}
  @Transactional(readOnly=true) public BatchPreview previewBatch(Batch b){AccessContext.requireEdit();check(b!=null,"Batch required");var s=visible(read());check(b.revision()!=null&&b.revision()==s.revision(),"Commitments changed; reload saved revisions");var next=new State(s.revision(),edited(s,b.edits()),s.payrolls(),s.exceptions(),s.operations());return new BatchPreview(s.revision(),occurrences(s,today().minusDays(31),today().plusMonths(6),today()),occurrences(next,today().minusDays(31),today().plusMonths(6),today()));}
  @Transactional(readOnly=true) public Workspace get(){var s=read();return new Workspace(today(),visible(s),ledger.position(today()).accounts().stream().filter(a->a.kind().equals("CASH")&&a.owned()).toList(),review.categories());}
  // Redact the entire dependent plan after source access is lost, including audit/operation responses.
  private boolean sourcesVisible(State s){return s.schedules().stream().flatMap(x->x.versions().stream()).allMatch(v->{try{account(v.rule().accountId());if(v.rule().direction().equals("TRANSFER")&&v.rule().counterpartId()!=null&&!v.rule().counterpartId().isBlank())account(v.rule().counterpartId());return true;}catch(RuntimeException e){return false;}});}
  private State visible(State s){check(sourcesVisible(s),"Access to a source account changed; restricted plan is hidden");return s;}
  @Transactional(readOnly=true) public Map<String,Boolean> operation(String id){var s=visible(read());return Map.of("completed",s.operations().containsKey(uuid(id)));}
  @Transactional(readOnly=true) public List<LedgerAudit> history(){visible(read());return audits.findAll().stream().filter(a->a.actorId.equals(AccessContext.userId())&&a.action.equals("COMMITMENT_CHANGE")).sorted(Comparator.comparing(a->a.recordedAt)).toList();}
  @Transactional public State payroll(PayrollEdit e){
    check(e!=null,"Payroll required");var s=visible(read());if(replay(s,e.revision(),e.operationId(),e))return s;
    return save(s,s.schedules(),preparedPayroll(s,e),s.exceptions(),e.operationId(),e.reason(),e);
  }
  private List<Payroll> preparedPayroll(State s,PayrollEdit e){
    String id=uuid(e.id());date(e.effectiveFrom());var r=e.rule();check(r!=null,"Payroll rule required");bounded(r.name(),100);
    check(r.day()>=1&&r.day()<=31&&r.weekend()!=null&&r.weekend().size()<=6&&new HashSet<>(r.weekend()).size()==r.weekend().size()&&r.weekend().stream().allMatch(d->d!=null&&d>=1&&d<=7),"Choose valid working week and pay day");
    check(Set.of("NONE","PREVIOUS","NEXT").contains(Objects.toString(r.adjustment(),"")),"Choose a working-day rule");
    check(r.holidays()!=null&&r.holidays().size()<=200&&r.exceptions()!=null&&r.exceptions().size()<=100,"Payroll calendar too large");r.holidays().forEach(this::date);
    r.exceptions().forEach((a,b)->{date(a);date(b);check(Math.abs(ChronoUnit.DAYS.between(a,b))<=31,"Payroll exception moves at most 31 days");});
    var ps=new ArrayList<>(s.payrolls());var old=ps.stream().filter(p->p.id().equals(id)).findFirst().orElse(null);
    check(e.payrollRevision()!=null&&e.payrollRevision()==(old==null?0:old.revision()),"Payroll changed; reload");
    var vs=new ArrayList<PayrollVersion>();if(old!=null){vs.addAll(old.versions());check(!e.effectiveFrom().isBefore(today())&&!e.effectiveFrom().isBefore(vs.getLast().from()),"Past payroll rules cannot be rewritten");ps.remove(old);}check(vs.size()<100,"Payroll history limit reached");vs.add(new PayrollVersion(e.effectiveFrom(),r));ps.add(new Payroll(id,old==null?1:old.revision()+1,List.copyOf(vs)));
    return List.copyOf(ps);
  }
  @Transactional(readOnly=true) public List<LocalDate> previewPayroll(PayrollEdit e){AccessContext.requireEdit();check(e!=null,"Payroll required");var s=visible(read());check(e.revision()!=null&&e.revision()==s.revision(),"Commitments changed; reload");var ps=preparedPayroll(s,e);var next=new State(s.revision(),s.schedules(),ps,s.exceptions(),s.operations());List<LocalDate> dates=new ArrayList<>();LocalDate start=e.effectiveFrom().isAfter(today())?e.effectiveFrom():today();var first=ps.stream().filter(p->p.id().equals(e.id())).findFirst().orElseThrow().versions().getFirst();for(int i=0;dates.size()<6&&i<8;i++){LocalDate nominal=start.plusMonths(i);if(calendarDate(first.rule(),YearMonth.from(nominal)).isBefore(first.from()))continue;LocalDate due=payDate(next,e.id(),nominal);if(!due.isBefore(start))dates.add(due);}return List.copyOf(dates);}
  private LocalDate payDate(State s,String id,LocalDate nominal){
    var p=s.payrolls().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Payroll calendar unavailable"));
    var month=YearMonth.from(nominal);
    PayrollVersion first=p.versions().getFirst();LocalDate planned=calendarDate(first.rule(),month);check(!planned.isBefore(first.from()),"Payroll calendar does not cover this occurrence");
    for(int i=1;i<p.versions().size();i++){var version=p.versions().get(i);if(!planned.isBefore(version.from())){var proposed=calendarDate(version.rule(),month);if(!proposed.isBefore(version.from()))planned=proposed;}}
    var delays=s.exceptions().stream().filter(e->e.date()!=null&&"OVERRIDE".equals(e.action())&&e.frozenRule()!=null&&"INCOME".equals(e.frozenRule().direction())&&"INCOME_LINKED".equals(e.frozenRule().frequency())&&Objects.equals(id,e.frozenRule().payrollId())&&YearMonth.from(LocalDate.parse(e.id().substring(e.id().indexOf('/')+1))).equals(month)).map(e->e.date().minusDays(e.frozenRule().offsetDays())).distinct().toList();
    check(delays.size()<=1,"Conflicting payroll occurrence dates; review the linked income overrides");if(!delays.isEmpty())return delays.getFirst();
    return planned;
  }
  private LocalDate calendarDate(PayrollRule rule,YearMonth month){
    LocalDate day=month.atDay(Math.min(rule.day(),month.lengthOfMonth()));
    if(rule.exceptions().containsKey(day))return rule.exceptions().get(day);
    if(!rule.adjustment().equals("NONE")){int n=0;while(rule.weekend().contains(day.getDayOfWeek().getValue())||rule.holidays().contains(day)){check(n++<31,"Payroll has no working day within 31 days");day=day.plusDays(rule.adjustment().equals("PREVIOUS")?-1:1);}}
    return day;
  }
  private List<LocalDate> slots(Rule r,LocalDate from,LocalDate to){
    var out=new ArrayList<LocalDate>();LocalDate begin=from.isAfter(r.anchor())?from:r.anchor();if(to.isBefore(begin))return out;
    switch(r.frequency()){
      case "ONCE"->{if(!r.anchor().isBefore(from)&&!r.anchor().isAfter(to))out.add(r.anchor());}
      case "WEEKLY","FORTNIGHTLY"->{int step=r.frequency().equals("WEEKLY")?7:14;long days=ChronoUnit.DAYS.between(r.anchor(),begin);LocalDate d=r.anchor().plusDays(((days+step-1)/step)*step);for(;!d.isAfter(to);d=d.plusDays(step))out.add(d);}
      default->{for(YearMonth m=YearMonth.from(begin);!m.isAfter(YearMonth.from(to));m=m.plusMonths(1)){LocalDate d=m.atDay(Math.min(r.anchor().getDayOfMonth(),m.lengthOfMonth()));if(!d.isBefore(begin)&&!d.isAfter(to))out.add(d);}}
    }return out;
  }
  private String fingerprint(LedgerEntry e){return hash(List.of(e.id,e.accountId,e.date,e.amountMinor,e.status,e.reviewState,e.kind,Objects.toString(e.categoryId,""),Objects.toString(e.linkType,""),Objects.toString(e.linkedEntryId,"")));}
  private boolean usableActual(Rule r,LedgerEntry e,boolean pending){
    if(e==null||!e.accountId.equals(r.accountId())||!e.kind.equals("ACTIVITY"))return false;
    if(r.direction().equals("TRANSFER")){
      if(pending||!"BOOKED".equals(e.status)||!"CONFIRMED".equals(e.reviewState)||e.amountMinor>=0||!LedgerService.isTransferRelationship(e)||e.linkedEntryId==null)return false;
      var peer=entries.findById(e.linkedEntryId).orElse(null);
      return peer!=null&&Objects.equals(peer.accountId,r.counterpartId())&&Objects.equals(peer.linkedEntryId,e.id)&&peer.amountMinor==-e.amountMinor&&peer.date.equals(e.date)&&peer.status.equals("BOOKED")&&peer.reviewState.equals("CONFIRMED")&&LedgerService.isTransferRelationship(peer);
    }
    return !LedgerService.isTransferRelationship(e)&&e.linkType==null&&(pending?e.status.equals("PENDING")&&e.amountMinor<0:e.status.equals("BOOKED")&&e.reviewState.equals("CONFIRMED"))&&(r.direction().equals("INCOME")?e.amountMinor>0:e.amountMinor<0)&&Objects.equals(e.categoryId,r.categoryId());
  }
  private Occurrence occurrence(State s,Schedule schedule,Version version,LocalDate nominal,LocalDate baseline){
    String id=schedule.id()+"/"+nominal;var edit=s.exceptions().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);
    Rule r=edit!=null&&edit.frozenRule()!=null?edit.frozenRule():version.rule();LocalDate due=r.frequency().equals("INCOME_LINKED")?payDate(s,r.payrollId(),nominal).plusDays(r.offsetDays()):nominal;long amount=money(r.amountEuro());
    if(edit!=null){if(edit.date()!=null)due=edit.date();if(edit.amountMinor()!=null)amount=edit.amountMinor();}
    List<String>warnings=new ArrayList<>();String state=due.isBefore(today())?"DATA_UNCONFIRMED":"UPCOMING";boolean suppressed=edit!=null&&edit.skip();String entryId=edit==null?null:edit.entryId();Long actual=null;
    if(suppressed)state="SKIPPED";
    else if(entryId!=null){var e=entries.findById(entryId).orElse(null);boolean exact=e!=null&&Objects.equals(edit.fingerprint(),fingerprint(e));
      if("MATCH".equals(edit.action())&&exact&&usableActual(r,e,false)){state="MATCHED";actual=Math.abs(e.amountMinor);suppressed=!e.date.isAfter(baseline);due=e.date;if(!suppressed)amount=actual;}
      else if("RESERVE".equals(edit.action())&&exact&&usableActual(r,e,true)&&!e.date.isAfter(baseline)){state="PENDING_RESERVED";suppressed=true;}
      else {state="MATCH_NEEDS_REVIEW";warnings.add("Linked activity changed, settled or was voided. Review match and baseline before relying on this outlook.");}
    }else if(due.isBefore(today())){var a=account(r.accountId());if(a.reconciledDate!=null&&!a.reconciledDate.isBefore(due)&&Objects.equals(a.reconciledRevision,a.revision))state="OVERDUE";}
    if(r.direction().equals("TRANSFER")&&(r.counterpartId()==null||r.counterpartId().isBlank()))warnings.add("Missing transfer counterpart; source debit retained and no destination credit assumed.");
    return new Occurrence(id,schedule.id(),nominal,due,r.name(),r.accountId(),r.categoryId(),r.direction(),r.certainty(),r.amountType(),amount,r.counterpartId(),state,entryId,actual,suppressed,List.copyOf(warnings));
  }
  private List<Occurrence> occurrences(State s,LocalDate from,LocalDate to,LocalDate baseline){
    visible(s);date(from);date(to);check(!to.isBefore(from),"End must follow start");
    var rows=new LinkedHashMap<String,Occurrence>();
    // Include moved occurrences whose nominal dates lie outside the visible window.
    LocalDate scanFrom=from.minusDays(MAX_RESOLVED_SHIFT_DAYS),scanTo=to.plusDays(MAX_RESOLVED_SHIFT_DAYS);
    for(var sc:s.schedules())for(int i=0;i<sc.versions().size();i++){var v=sc.versions().get(i);if(v.cancelled())continue;
      LocalDate boundary=i+1<sc.versions().size()?sc.versions().get(i+1).from():null;
      LocalDate begin=scanFrom.isAfter(v.from().minusDays(MAX_RESOLVED_SHIFT_DAYS))?scanFrom:v.from().minusDays(MAX_RESOLVED_SHIFT_DAYS),end=scanTo;
      if(boundary!=null&&boundary.plusDays(MAX_RESOLVED_SHIFT_DAYS).isBefore(end))end=boundary.plusDays(MAX_RESOLVED_SHIFT_DAYS);
      if(v.rule().end()!=null&&v.rule().end().plusDays(MAX_RESOLVED_SHIFT_DAYS).isBefore(end))end=v.rule().end().plusDays(MAX_RESOLVED_SHIFT_DAYS);
      for(var nominal:slots(v.rule(),begin,end)){var selected=effectiveVersion(s,sc,nominal);if(selected==null)continue;var o=occurrence(s,sc,selected,nominal,baseline);if(!o.date().isBefore(from)&&!o.date().isAfter(to))rows.put(o.id(),o);check(rows.size()<=10000,"Too many occurrences; review and close old obligations before projecting");}
    }
    for(var edit:s.exceptions()){var o=find(s,edit.id());if(!o.date().isBefore(from)&&!o.date().isAfter(to)){var sc=s.schedules().stream().filter(x->x.id().equals(o.scheduleId())).findFirst().orElseThrow();var frozen=new Version(o.nominalDate(),false,edit.frozenRule());rows.put(o.id(),occurrence(s,sc,frozen,o.nominalDate(),baseline));}}
    check(rows.size()<=10000,"Too many occurrences; shorten the selected range");return rows.values().stream().sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::id)).toList();
  }
  @Transactional(readOnly=true) public List<Occurrence> upcoming(LocalDate from,LocalDate to){date(from);date(to);check(ChronoUnit.DAYS.between(from,to)<=1096,"Upcoming range is limited to three years");return occurrences(read(),from,to,today());}
  @Transactional(readOnly=true) public List<Candidate> candidates(String occurrenceId){
    var s=visible(read());var o=find(s,occurrenceId);var r=ruleFor(s,o);var a=account(r.accountId());
    return entries.findByAccountIdOrderByDateAscReferenceAsc(a.id).stream().filter(e->usableActual(r,e,false)||usableActual(r,e,true)).map(e->new Candidate(e.id,e.accountId,e.date,e.description,e.amountMinor,e.status,a.revision)).toList();
  }
  // Resolve identity before applying a query window: an earlier eligible version owns
  // its occurrence even when a later offset would move that identity across a boundary.
  private Version effectiveVersion(State s,Schedule sc,LocalDate nominal){
    for(int i=0;i<sc.versions().size();i++){var v=sc.versions().get(i);if(v.cancelled()||!slots(v.rule(),nominal,nominal).contains(nominal))continue;var o=occurrence(s,sc,v,nominal,today());if(!o.date().isBefore(v.from())&&(v.rule().end()==null||!o.date().isAfter(v.rule().end()))&&(i+1==sc.versions().size()||o.date().isBefore(sc.versions().get(i+1).from())))return v;}
    return null;
  }
  private Rule ruleFor(State s,Occurrence o){var edit=s.exceptions().stream().filter(x->x.id().equals(o.id())).findFirst().orElse(null);if(edit!=null&&edit.frozenRule()!=null)return edit.frozenRule();var sc=s.schedules().stream().filter(x->x.id().equals(o.scheduleId())).findFirst().orElseThrow();return Objects.requireNonNull(effectiveVersion(s,sc,o.nominalDate())).rule();}
  private Occurrence find(State s,String id){
    try{int slash=id.indexOf('/');String schedule=uuid(id.substring(0,slash));LocalDate nominal=LocalDate.parse(id.substring(slash+1));date(nominal);var sc=s.schedules().stream().filter(x->x.id().equals(schedule)).findFirst().orElseThrow();var saved=s.exceptions().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);
      if(saved!=null&&saved.frozenRule()!=null)return occurrence(s,sc,new Version(nominal,false,saved.frozenRule()),nominal,today());
      var selected=effectiveVersion(s,sc,nominal);if(selected!=null)return occurrence(s,sc,selected,nominal,today());
      throw new IllegalArgumentException("Unknown occurrence");
    }catch(RuntimeException e){throw new IllegalArgumentException("Unknown or unavailable occurrence");}
  }
  @Transactional public State exception(ExceptionInput e){
    check(e!=null,"Occurrence change required");var s=visible(read());if(replay(s,e.revision(),e.operationId(),e))return s;var o=find(s,e.occurrenceId());Rule rule=ruleFor(s,o);
    check(Set.of("OVERRIDE","MATCH","RESERVE","UNLINK").contains(Objects.toString(e.action(),"")),"Choose a valid occurrence action");
    var edits=new ArrayList<>(s.exceptions());var previous=edits.stream().filter(x->x.id().equals(o.id())).findFirst().orElse(null);edits.removeIf(x->x.id().equals(o.id()));
    if(e.action().equals("UNLINK")){check(previous!=null,"No saved occurrence change");edits.add(new OccurrenceEdit(o.id(),previous.date(),previous.amountMinor(),previous.skip(),null,null,"OVERRIDE",rule));}
    else if(e.action().equals("OVERRIDE")){check((!o.date().isBefore(today())||o.direction().equals("INCOME"))&&!o.state().equals("MATCHED")&&!o.state().equals("PENDING_RESERVED"),"Past expenses or linked occurrences cannot be overridden; unlink explicitly first");if(e.date()!=null){date(e.date());check(!e.date().isBefore(today())&&Math.abs(ChronoUnit.DAYS.between(o.nominalDate(),e.date()))<=62,"Override date must be future and within 62 days of nominal date");}check(!o.date().isBefore(today())||e.date()!=null,"Retiming overdue income requires an explicit future date");edits.add(new OccurrenceEdit(o.id(),e.date(),e.amountEuro()==null?null:money(e.amountEuro()),e.skip(),null,null,e.action(),rule));}
    else{var a=account(o.accountId());check(e.accountRevision()!=null&&e.accountRevision()==a.revision,"Account changed; reload candidates");check(e.entryId()!=null,"Choose reviewed activity");var entry=entries.findById(e.entryId()).orElse(null);check(usableActual(rule,entry,e.action().equals("RESERVE")),"Choose an approved booked actual or matching pending debit; transfers require approved reciprocal booked legs on the same date");check(!entry.date.isAfter(today()),"Future actuals are not supported");check(s.exceptions().stream().noneMatch(x->!x.id().equals(o.id())&&(Objects.equals(x.entryId(),entry.id)||Objects.equals(x.entryId(),entry.linkedEntryId))),"Activity is already linked to another occurrence");edits.add(new OccurrenceEdit(o.id(),previous==null?null:previous.date(),previous==null?null:previous.amountMinor(),false,entry.id,fingerprint(entry),e.action(),rule));}
    return save(s,s.schedules(),s.payrolls(),edits,e.operationId(),e.reason(),e);
  }
  private long add(long a,long b){long n=Math.addExact(a,b);check(n>=-9_007_199_254_740_991L&&n<=9_007_199_254_740_991L,"Projection exceeds safe money range");return n;}
  @Transactional(readOnly=true) public Comparison compare(ComparisonInput in){
    check(in!=null,"Comparison required");var s=visible(read());check(in.revision()!=null&&in.revision()==s.revision(),"Commitments changed; reload before comparing");
    var base=outlook(in.baseline(),in.end(),false,in.cashOnly());var rows=new ArrayList<>(base.occurrences());var warnings=new ArrayList<>(base.warnings());
    if(in.delayedIncomeId()!=null&&!in.delayedIncomeId().isBlank()){
      var original=rows.stream().filter(o->o.id().equals(in.delayedIncomeId())&&o.direction().equals("INCOME")&&o.certainty().equals("EXPECTED")&&!o.suppressed()&&!o.state().equals("MATCHED")).findFirst().orElseThrow(()->new IllegalArgumentException("Choose an unmatched expected income"));
      check(in.delayedUntil()!=null&&!in.delayedUntil().isBefore(original.date())&&!in.delayedUntil().isAfter(in.end()),"Choose a later income date inside the horizon");long delay=ChronoUnit.DAYS.between(original.date(),in.delayedUntil());Rule incomeRule=ruleFor(s,original);
      for(int i=0;i<rows.size();i++){var o=rows.get(i);Rule r=ruleFor(s,o);boolean linked=incomeRule.frequency().equals("INCOME_LINKED")&&r.frequency().equals("INCOME_LINKED")&&Objects.equals(incomeRule.payrollId(),r.payrollId())&&YearMonth.from(o.nominalDate()).equals(YearMonth.from(original.nominalDate()))&&s.exceptions().stream().noneMatch(e->e.id().equals(o.id()));if(o.id().equals(original.id())||linked){var date=o.date().plusDays(delay);check(!date.isAfter(in.end()),"Delayed linked bill falls outside the horizon; extend the outlook");rows.set(i,new Occurrence(o.id(),o.scheduleId(),o.nominalDate(),date,o.name(),o.accountId(),o.categoryId(),o.direction(),o.certainty(),o.amountType(),o.amountMinor(),o.counterpartId(),o.state(),o.entryId(),o.actualMinor(),o.suppressed(),o.warnings()));}}
    }else check(in.delayedUntil()==null,"Select the income to delay");
    if(in.transferEuro()!=null&&!in.transferEuro().isBlank()){
      long amount=money(in.transferEuro());account(in.sourceAccount());account(in.destinationAccount());check(!Objects.equals(in.sourceAccount(),in.destinationAccount()),"Transfer accounts must differ");check(base.accounts().stream().anyMatch(a->a.id().equals(in.sourceAccount()))&&base.accounts().stream().anyMatch(a->a.id().equals(in.destinationAccount())),"Transfer accounts must exist at the baseline");check(in.transferDate()!=null&&in.transferDate().isAfter(in.baseline())&&!in.transferDate().isAfter(in.end()),"Transfer date must lie after baseline and inside horizon");rows.add(new Occurrence("what-if-transfer","what-if",in.transferDate(),in.transferDate(),"Unsaved planned transfer",in.sourceAccount(),null,"TRANSFER","EXPECTED","FIXED",amount,in.destinationAccount(),"UPCOMING",null,null,false,List.of()));
    }
    warnings.add("Unsaved what-if: no schedules, ledger activity, allocations or audit were changed.");if(in.uncertain())warnings.add("Upside only: includes uncertain income.");
    List<CashLine> lines=new ArrayList<>();for(var a:base.accounts())lines.add(project(a.id(),a.name(),a.startingMinor(),in.baseline(),in.end(),rows,in.uncertain(),in.cashOnly()));
    var household=project(null,base.household().name(),base.household().startingMinor(),in.baseline(),in.end(),rows,in.uncertain(),in.cashOnly());return new Comparison(base,new Outlook(in.baseline(),in.end(),false,List.copyOf(warnings),List.copyOf(rows),household,List.copyOf(lines)));
  }
  private LocalDate resolvedStart(State state,LocalDate baseline){
    LocalDate earliest=state.schedules().stream().flatMap(s->s.versions().stream()).map(v->v.rule().anchor()).min(LocalDate::compareTo).orElse(baseline).minusDays(MAX_RESOLVED_SHIFT_DAYS);
    return earliest.isBefore(LocalDate.of(2000,1,1))?LocalDate.of(2000,1,1):earliest.isBefore(baseline)?earliest:baseline;
  }
  @Transactional(readOnly=true) public Outlook outlook(LocalDate baseline,LocalDate end,boolean uncertain,boolean cashOnly){
    date(baseline);date(end);check(!baseline.isAfter(today())&&!baseline.isBefore(today().minusYears(1))&&end.isAfter(baseline)&&!end.isAfter(baseline.plusMonths(12)),"Choose 1 to 12 calendar months after a baseline within the past year");
    var state=visible(read());var position=ledger.position(baseline);var cash=position.accounts().stream().filter(a->a.owned()&&a.kind().equals("CASH")).toList();
    var ids=cash.stream().map(LedgerService.AccountView::id).collect(java.util.stream.Collectors.toSet());
    // Monthly anchor/calendar day difference (30), calendar movement (31), and
    // offset (31) fit within 124 days; linked income overrides can move 62 days
    // plus the two linked offsets (62). Keep all supported pre-anchor obligations.
    var all=occurrences(state,resolvedStart(state,baseline),end,baseline);var warnings=new ArrayList<String>();
    check(all.stream().noneMatch(o->o.state().equals("MATCH_NEEDS_REVIEW")),"Linked activity changed or settled; review its match in Upcoming before calculating a new outlook");
    check(state.schedules().stream().flatMap(x->x.versions().stream()).allMatch(v->ids.contains(v.rule().accountId())&&(v.rule().counterpartId()==null||v.rule().counterpartId().isBlank()||ids.contains(v.rule().counterpartId()))),"Starting evidence excludes a scheduled account; choose a later baseline");
    for(var o:all){warnings.addAll(o.warnings());if(o.state().equals("OVERDUE")||o.state().equals("DATA_UNCONFIRMED"))warnings.add(o.name()+": "+o.state()+"; unpaid expenses stay reserved and overdue income is not assumed received.");}
    boolean ready=!cash.isEmpty()&&cash.stream().allMatch(LedgerService.AccountView::reconciled)&&baseline.equals(today())&&warnings.isEmpty()&&!uncertain;
    if(cash.isEmpty())warnings.add("No owned cash accounts. Create and review account evidence in Money position. Existing dated/manual plans remain available in Starting evidence & saved plan.");
    if(cash.stream().anyMatch(a->!a.reconciled()))warnings.add("Illustrative: starting account evidence is not reconciled for this date and revision.");
    if(!baseline.equals(today()))warnings.add("Historical baseline: later actual activity is not incorporated automatically. Refresh after reviewing actuals.");
    if(uncertain)warnings.add("Upside scenario includes uncertain income; never current cash.");
    warnings.add("Forecast only. Pending debits are reserved once. Budgets do not move cash. Expenses precede same-day income. Existing dated plan is retained separately; do not enter the same expectation twice.");
    List<CashLine> lines=new ArrayList<>();for(var a:cash)lines.add(project(a.id(),a.name(),add(a.bookedMinor(),-a.pendingDebitMinor()),baseline,end,all,uncertain,cashOnly));
    long starting=0;for(var a:lines)starting=add(starting,a.startingMinor());var household=project(null,"Permitted owned household cash",starting,baseline,end,all,uncertain,cashOnly);
    return new Outlook(baseline,end,ready,List.copyOf(new LinkedHashSet<>(warnings)),all,household,List.copyOf(lines));
  }
  private CashLine project(String account,String name,long starting,LocalDate baseline,LocalDate end,List<Occurrence> rows,boolean uncertain,boolean cashOnly){
    long balance=starting,low=starting;LocalDate lowDate=baseline,shortfall=balance<0?baseline:null;List<Point> days=new ArrayList<>();
    for(LocalDate date=baseline.plusDays(1);!date.isAfter(end);date=date.plusDays(1)){
      long out=0,in=0;List<String> source=new ArrayList<>();
      for(var o:rows){if(o.suppressed()||o.date().isAfter(date)||(!o.date().equals(date)&&!(date.equals(baseline.plusDays(1))&&!o.date().isAfter(baseline))))continue;
        boolean own=account==null||account.equals(o.accountId());long amount=o.amountMinor();
        if(o.direction().equals("TRANSFER")){
          boolean paired=o.counterpartId()!=null&&!o.counterpartId().isBlank();
          // Household transfer legs net to zero, including the daily low. No fictional household debit.
          if(account==null&&paired)continue;
          if(own)out=add(out,amount);if(account!=null&&paired&&account.equals(o.counterpartId()))in=add(in,amount);
        }else if(own&&o.direction().equals("EXPENSE"))out=add(out,amount);
        else if(own&&(o.state().equals("MATCHED")||(!cashOnly&&!o.date().isBefore(today())&&(o.certainty().equals("EXPECTED")||uncertain))))in=add(in,amount);
        if(own||Objects.equals(account,o.counterpartId()))source.add(o.id());
      }
      long dailyLow=add(balance,-out),closing=add(dailyLow,in);if(dailyLow<low){low=dailyLow;lowDate=date;}if(shortfall==null&&dailyLow<0)shortfall=date;days.add(new Point(date,balance,out,in,dailyLow,closing,List.copyOf(source)));balance=closing;
    }return new CashLine(account,name,starting,low,lowDate,balance,shortfall,List.copyOf(days));
  }
  private static final String HEADER="id,revision,effectiveFrom,cancelled,name,accountId,categoryId,direction,certainty,amountType,amountEuro,frequency,anchor,end,payrollId,offsetDays,counterpartId";
  private String cell(String s){String v=Objects.toString(s,"");if(v.startsWith("'")||v.matches("(?s)^\\s*[=+@\\-].*"))v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}
  @Transactional(readOnly=true) public String csv(){var s=visible(read());var out=new StringBuilder(HEADER+"\r\n");for(var sc:s.schedules()){var v=sc.versions().getLast();var r=v.rule();var cols=Arrays.asList(sc.id(),""+sc.revision(),today().isAfter(v.from())?today().toString():v.from().toString(),""+v.cancelled(),r.name(),r.accountId(),r.categoryId(),r.direction(),r.certainty(),r.amountType(),r.amountEuro(),r.frequency(),""+r.anchor(),Objects.toString(r.end(),""),Objects.toString(r.payrollId(),""),""+r.offsetDays(),Objects.toString(r.counterpartId(),""));out.append(String.join(",",cols.stream().map(this::cell).toList())).append("\r\n");}return out.toString();}
  private List<List<String>> parseCsv(String csv){check(csv!=null&&csv.length()<=200000,"CSV too large");List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
    for(int i=0;i<csv.length();i++){char c=csv.charAt(i);if(quoted){if(c=='\"'){if(i+1<csv.length()&&csv.charAt(i+1)=='\"'){field.append('\"');i++;}else{quoted=false;closed=true;}}else field.append(c);}
      else if(c==','||c=='\n'||c=='\r'){row.add(field.toString());field.setLength(0);closed=false;if(c!=','){if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;rows.add(List.copyOf(row));row.clear();check(rows.size()<=101,"CSV limited to 100 rows");}}
      else if(c=='\"'){check(field.isEmpty()&&!closed,"Invalid CSV quoting");quoted=true;}else{check(!closed,"Unexpected text after CSV quote");field.append(c);}}
    check(!quoted,"Unclosed CSV quote");if(!field.isEmpty()||!row.isEmpty()){row.add(field.toString());rows.add(List.copyOf(row));}return rows;
  }
  private String nullable(String s){return s.isBlank()?null:s;}
  private CsvPreview preview(State s,String csv){
    var rows=parseCsv(csv);check(!rows.isEmpty()&&String.join(",",rows.getFirst()).equals(HEADER),"CSV columns must exactly match exported template");List<CsvRow> result=new ArrayList<>();Set<String> seen=new HashSet<>();
    for(int i=1;i<rows.size();i++){Edit edit=null;List<String> errors=new ArrayList<>();try{var c=rows.get(i);check(c.size()==17,"Expected 17 columns");check(c.get(3).equals("true")||c.get(3).equals("false"),"cancelled must be true or false");String name=c.get(4);if(name.startsWith("'"))name=name.substring(1);edit=new Edit(c.get(0),Long.parseLong(c.get(1)),LocalDate.parse(c.get(2)),Boolean.parseBoolean(c.get(3)),new Rule(name,c.get(5),c.get(6),c.get(7),c.get(8),c.get(9),c.get(10),c.get(11),LocalDate.parse(c.get(12)),c.get(13).isBlank()?null:LocalDate.parse(c.get(13)),nullable(c.get(14)),Integer.parseInt(c.get(15).replaceFirst("^'","")),nullable(c.get(16))));check(s.schedules().stream().anyMatch(x->x.id().equals(c.get(0))),"CSV updates require an existing stable ID");check(seen.add(c.get(0)),"Duplicate stable ID");edited(s,List.of(edit));}catch(RuntimeException e){errors.add(Objects.toString(e.getMessage(),"Invalid CSV row"));}result.add(new CsvRow(i+1,edit,List.copyOf(errors)));}
    return new CsvPreview(s.revision(),List.copyOf(result),!result.isEmpty()&&result.stream().allMatch(r->r.errors().isEmpty()));
  }
  @Transactional(readOnly=true) public CsvPreview preview(CsvInput in){check(in!=null,"CSV required");return preview(visible(read()),in.csv());}
  @Transactional public State apply(CsvApply in){check(in!=null,"CSV required");var s=visible(read());if(replay(s,in.revision(),in.operationId(),in))return s;var preview=preview(s,in.csv());check(preview.applicable(),"CSV rejected; download complete rejection report and correct every row");return save(s,edited(s,preview.rows().stream().map(CsvRow::edit).toList()),s.payrolls(),s.exceptions(),in.operationId(),in.reason(),in);}
}
