package com.householdfinance.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.AccessContext;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Allocation decisions never create bank movements or forecast income. All amounts are EUR cents. */
@Service
public class BudgetService {
  public record LineInput(String categoryId, String amountEuro, boolean accumulate, LocalDate dueDate) {}
  public record Line(String categoryId, String categoryName, long amountMinor, boolean accumulate, LocalDate dueDate) {}
  public record TemplateInput(Long revision, String name, String incomeEuro, String grouping, Integer payDay, List<LineInput> lines, String reason) {}
  public record Template(String id, long revision, String name, long incomeMinor, String grouping, int payDay, List<Line> lines) {
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public long allocatedMinor(){return lines.stream().mapToLong(Line::amountMinor).sum();}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public long unallocatedMinor(){return incomeMinor-allocatedMinor();}
  }
  public record Instantiate(Long revision, LocalDate anchor, String reason) {}
  public record Period(String id, long revision, String templateId, long templateRevision, LocalDate start, LocalDate end,
      long originalIncomeMinor, long incomeMinor, List<Line> original, List<Line> lines, Map<String,Long> rollover,
      String predecessorId, Long predecessorRevision, Instant createdAt) {}
  public record Change(Long revision, String incomeEuro, List<LineInput> lines, String reason) {}
  public record Apply(Long revision, Long templateRevision, String mode, String reason, Map<String,Long> periodRevisions) {}
  public record Row(String categoryId, String name, long originalMinor, long allocatedMinor, long rolloverMinor, long actualMinor, long remainingMinor, long varianceMinor, boolean accumulate, LocalDate dueDate) {}
  public record Report(Period period, List<Row> rows, long allocatedMinor, long unallocatedMinor, long actualMinor, long confirmedIncomeMinor, int unresolved, List<String> warnings) {}
  private final LedgerReviewRepository records;
  private final LedgerEntryRepository entries;
  private final LedgerAccountRepository accounts;
  private final LedgerAuditRepository audits;
  private final ActivityReviewService review;
  private final ObjectMapper json;
  public BudgetService(LedgerReviewRepository r, LedgerEntryRepository e, LedgerAccountRepository a, LedgerAuditRepository h, ActivityReviewService v, ObjectMapper j) {records=r;entries=e;accounts=a;audits=h;review=v;json=j;}
  private void check(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
  private String text(String s,int max){check(s!=null&&!s.isBlank()&&s.strip().length()<=max,"A bounded name or reason is required");return s.strip();}
  private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException("Cannot encode budget",e);}}
  private <T>T decode(LedgerReviewRecord r,Class<T> t){try{return json.readValue(r.payload,t);}catch(Exception e){throw new IllegalStateException("Invalid saved budget",e);}}
  private List<LedgerReviewRecord> owned(String kind){AccessContext.requireRead();return records.findAll().stream().filter(r->r.kind.equals(kind)&&AccessContext.owns(r.ownerId)).toList();}
  private LedgerReviewRecord record(String id,String kind){return owned(kind).stream().filter(r->r.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown budget"));}
  private void revision(long actual,Long expected){AccessContext.requireEdit();check(expected!=null&&actual==expected,"Budget changed; reload before saving");}
  private long amount(String s){long n=LedgerService.money(s);check(n>=0,"Allocations and planned income must be nonnegative");return n;}
  private long sum(long a,long b){long n=Math.addExact(a,b);check(n>=-9_007_199_254_740_991L&&n<=9_007_199_254_740_991L,"Budget total exceeds supported range");return n;}
  private long total(List<Line> lines){return lines.stream().mapToLong(Line::amountMinor).reduce(0,this::sum);}
  private List<Line> lines(List<LineInput> input,LocalDate start,LocalDate end){
    check(input!=null&&!input.isEmpty()&&input.size()<=100,"Choose 1 to 100 category allocations");
    Map<String,ActivityReviewService.Category> categories=new HashMap<>();review.categories().forEach(c->categories.put(c.id(),c));
    Set<String> seen=new HashSet<>();var result=new ArrayList<Line>();
    for(var i:input){check(i!=null,"Allocation required");var c=categories.get(i.categoryId());check(c!=null&&!c.retired(),"Choose an active owned category");check(seen.add(c.id()),"Allocate each category once");
      check(i.dueDate()==null||(start!=null&&!i.dueDate().isBefore(start)&&i.dueDate().isBefore(end)),"Allocation date must lie in the period; templates have no dated allocations");
      result.add(new Line(c.id(),c.name(),amount(i.amountEuro()),i.accumulate(),i.dueDate()));}
    return List.copyOf(result);
  }
  private void persist(String id,String kind,long revision,Object payload,String action,String reason,Object before){
    String why=text(reason,200);var r=new LedgerReviewRecord();r.id=id;r.ownerId=AccessContext.userId();r.kind=kind;r.revision=revision;r.payload=encode(payload);records.save(r);
    var h=new LedgerAudit();h.id=UUID.randomUUID().toString();h.accountId=AccessContext.publicationId();h.recordedAt=Instant.now();h.action=action;h.source=why;h.beforeJson=encode(before);h.afterJson=encode(payload);audits.save(h);
  }
  @Transactional(readOnly=true) public List<Template> templates(){return owned("BUDGET_TEMPLATE").stream().map(r->decode(r,Template.class)).sorted(Comparator.comparing(Template::name)).toList();}
  @Transactional(readOnly=true) public List<Period> periods(){return owned("BUDGET_PERIOD").stream().map(r->decode(r,Period.class)).sorted(Comparator.comparing(Period::start).reversed()).toList();}
  @Transactional public Template template(String id,TemplateInput input){
    AccessContext.requireEdit();check(input!=null,"Template required");Template before=id==null?null:decode(record(id,"BUDGET_TEMPLATE"),Template.class);if(before!=null)revision(before.revision(),input.revision());
    check(before!=null||templates().size()<100,"Template limit reached");String grouping=Objects.toString(input.grouping(),"");check(Set.of("CALENDAR","PAY_DAY").contains(grouping),"Choose calendar or pay-day grouping");
    int pay=input.payDay()==null?1:input.payDay();check(pay>=1&&pay<=31,"Pay day must be 1 to 31");long income=amount(input.incomeEuro());var ls=lines(input.lines(),null,null);check(total(ls)<=income,"Allocations exceed planned income");
    var t=new Template(id==null?UUID.randomUUID().toString():id,before==null?0:before.revision()+1,text(input.name(),80),income,grouping,grouping.equals("CALENDAR")?1:pay,ls);
    persist(t.id(),"BUDGET_TEMPLATE",t.revision(),t,"BUDGET_TEMPLATE",input.reason(),before);return t;
  }
  private LocalDate boundary(YearMonth month,int day){return month.atDay(Math.min(day,month.lengthOfMonth()));}
  private void activeTemplate(Template t){var active=review.categories().stream().filter(c->!c.retired()).map(ActivityReviewService.Category::id).toList();for(var l:t.lines())check(active.contains(l.categoryId()),"Update the template to use active categories before applying it");}
  @Transactional public Period instantiate(String id,Instantiate input){
    check(input!=null&&input.anchor()!=null,"Choose a period date");var t=decode(record(id,"BUDGET_TEMPLATE"),Template.class);revision(t.revision(),input.revision());
    activeTemplate(t);
    check(input.anchor().getYear()>=2000&&input.anchor().getYear()<=2200,"Choose a supported period year");YearMonth month=YearMonth.from(input.anchor());LocalDate start=boundary(month,t.payDay());if(input.anchor().isBefore(start))start=boundary(month.minusMonths(1),t.payDay());LocalDate end=boundary(YearMonth.from(start).plusMonths(1),t.payDay());
    var ps=periods();check(ps.size()<600,"Period limit reached");for(var p:ps)check(!start.isBefore(p.end())||!p.start().isBefore(end),"Period overlaps an existing budget; use its allocations instead");
    Period previous=null;for(var p:ps)if(p.end().equals(start)){previous=p;break;}
    Map<String,Long> rollover=new LinkedHashMap<>();if(previous!=null){var report=report(previous);for(var row:report.rows())if(row.accumulate()&&t.lines().stream().anyMatch(l->l.categoryId().equals(row.categoryId())))rollover.put(row.categoryId(),row.remainingMinor());}
    var p=new Period(UUID.randomUUID().toString(),0,t.id(),t.revision(),start,end,t.incomeMinor(),t.incomeMinor(),t.lines(),t.lines(),Map.copyOf(rollover),previous==null?null:previous.id(),previous==null?null:previous.revision(),Instant.now());
    persist(p.id(),"BUDGET_PERIOD",0,p,"BUDGET_CREATE",input.reason(),null);return p;
  }
  private Period period(String id){return decode(record(id,"BUDGET_PERIOD"),Period.class);}
  private Period updated(Period p,long income,List<Line> ls,long templateRevision){return new Period(p.id(),p.revision()+1,p.templateId(),templateRevision,p.start(),p.end(),p.originalIncomeMinor(),income,p.original(),ls,p.rollover(),p.predecessorId(),p.predecessorRevision(),p.createdAt());}
  @Transactional public Period change(String id,Change input){
    check(input!=null,"Explicit allocations required");var p=period(id);revision(p.revision(),input.revision());long income=amount(input.incomeEuro());var ls=lines(input.lines(),p.start(),p.end());check(total(ls)<=income,"Choose allocations within the revised planned income");
    // Do not silently discard an accumulated balance when changing categories.
    for(var key:p.rollover().keySet())check(ls.stream().anyMatch(l->l.categoryId().equals(key)),"Keep categories with carried balances in this period");
    var next=updated(p,income,ls,p.templateRevision());persist(id,"BUDGET_PERIOD",next.revision(),next,"BUDGET_REALLOCATE",input.reason(),p);return next;
  }
  @Transactional public List<Period> apply(String id,Apply input){
    check(input!=null,"Explicit template application required");var selected=period(id);revision(selected.revision(),input.revision());var t=decode(record(selected.templateId(),"BUDGET_TEMPLATE"),Template.class);revision(t.revision(),input.templateRevision());
    activeTemplate(t);
    check(Set.of("SELECTED","FORWARD").contains(Objects.toString(input.mode(),"")),"Choose selected or forward periods");
    var targets=periods().stream().filter(p->p.id().equals(id)||(input.mode().equals("FORWARD")&&p.templateId().equals(t.id())&&!p.start().isBefore(selected.start()))).toList();
    if(input.mode().equals("FORWARD"))for(var p:targets)check(input.periodRevisions()!=null&&Objects.equals(input.periodRevisions().get(p.id()),p.revision()),"A forward period changed or was added; reload before applying the template");
    // Global mutation admission serializes this complete batch with all ledger and budget writes.
    for(var p:targets)for(String key:p.rollover().keySet())check(t.lines().stream().anyMatch(l->l.categoryId().equals(key)),"Template would remove a carried balance; reallocate explicitly");
    var result=new ArrayList<Period>();for(var p:targets){var ls=t.lines().stream().map(l->new Line(l.categoryId(),l.categoryName(),l.amountMinor(),l.accumulate(),p.lines().stream().filter(old->old.categoryId().equals(l.categoryId())).map(Line::dueDate).filter(Objects::nonNull).findFirst().orElse(null))).toList();
      var next=updated(p,t.incomeMinor(),ls,t.revision());persist(p.id(),"BUDGET_PERIOD",next.revision(),next,"BUDGET_APPLY",input.reason(),p);result.add(next);}return result;
  }
  private Report report(Period p){
    var all=entries.findAll();Set<String> ownedAccounts=new HashSet<>();accounts.findAll().stream().filter(a->AccessContext.owns(a.ownerId)).forEach(a->ownedAccounts.add(a.id));
    Map<String,LedgerEntry> byId=new HashMap<>();all.stream().filter(e->ownedAccounts.contains(e.accountId)).forEach(e->byId.put(e.id,e));
    Map<String,Long> actual=new HashMap<>();int unresolved=0;long income=0;var warnings=new ArrayList<String>();
    for(var e:byId.values())if(!e.date.isBefore(p.start())&&e.date.isBefore(p.end())&&!e.status.equals("VOID")){
      if(!e.status.equals("BOOKED")||!e.reviewState.equals("CONFIRMED")){unresolved++;continue;}
      if(LedgerService.isTransferRelationship(e)||!e.kind.equals("ACTIVITY"))continue;
      boolean refund=Set.of("REFUND","REVERSAL").contains(Objects.toString(e.linkType,""));String cat=e.categoryId;
      if(refund){var original=byId.get(e.linkedEntryId);if(original==null||!original.reviewState.equals("CONFIRMED")||original.categoryId==null){unresolved++;continue;}cat=original.categoryId;}
      long spend=LedgerService.spendingContribution(e);if(cat!=null)actual.merge(cat,spend,this::sum);else if(spend!=0)unresolved++;
      if(e.amountMinor>0&&!refund)income=sum(income,e.amountMinor);
    }
    Map<String,Line> original=new LinkedHashMap<>();p.original().forEach(l->original.put(l.categoryId(),l));Map<String,Line> current=new LinkedHashMap<>();p.lines().forEach(l->current.put(l.categoryId(),l));Set<String> keys=new LinkedHashSet<>(current.keySet());keys.addAll(original.keySet());keys.addAll(actual.keySet());
    Map<String,String> names=new HashMap<>();review.categories().forEach(c->names.put(c.id(),c.name()));var rows=new ArrayList<Row>();
    for(String key:keys){var l=current.get(key);var o=original.get(key);long base=o==null?0:o.amountMinor(),allocated=l==null?0:l.amountMinor(),carry=p.rollover().getOrDefault(key,0L),spent=actual.getOrDefault(key,0L);
      rows.add(new Row(key,names.getOrDefault(key,l!=null?l.categoryName():o!=null?o.categoryName():"Unallocated category"),base,allocated,carry,spent,sum(sum(allocated,carry),-spent),sum(base,-spent),l!=null&&l.accumulate(),l==null?null:l.dueDate()));}
    warnings.add("Planned income and allocations are not cash or bank movements. Actuals use the ledger activity date, not allocation dates. End date is exclusive.");
    if(unresolved>0)warnings.add("Pending, provisional or uncategorised activity is excluded; review before relying on variance.");
    if(p.predecessorId()!=null){var predecessor=period(p.predecessorId());var fresh=reportWithoutCarryRecursion(predecessor);if(predecessor.revision()!=p.predecessorRevision()||p.rollover().entrySet().stream().anyMatch(e->!Objects.equals(e.getValue(),fresh.get(e.getKey()))))warnings.add("Earlier period changed after rollover; carried amounts remain the recorded snapshot. Adjust this period explicitly with a reason.");}
    if(income<p.incomeMinor())warnings.add("Confirmed income is below planned income; late or uncertain pay is not available cash. Choose revised allocations explicitly.");
    if(income>p.incomeMinor())warnings.add("Confirmed income exceeds plan. Keep surplus unallocated or choose allocations explicitly; no automatic savings or pension instruction.");
    return new Report(p,List.copyOf(rows),total(p.lines()),p.incomeMinor()-total(p.lines()),actual.values().stream().mapToLong(Long::longValue).reduce(0,this::sum),income,unresolved,List.copyOf(warnings));
  }
  private Map<String,Long> reportWithoutCarryRecursion(Period p){
    // Only the immediate predecessor matters; omit its predecessor to avoid recursive historical reads.
    var copy=new Period(p.id(),p.revision(),p.templateId(),p.templateRevision(),p.start(),p.end(),p.originalIncomeMinor(),p.incomeMinor(),p.original(),p.lines(),p.rollover(),null,null,p.createdAt());
    Map<String,Long> values=new HashMap<>();report(copy).rows().forEach(r->values.put(r.categoryId(),r.remainingMinor()));return values;
  }
  @Transactional(readOnly=true) public Report report(String id){AccessContext.requireRead();return report(period(id));}
  private boolean concerns(LedgerAudit h,String id){try{return h.afterJson!=null&&json.readTree(h.afterJson).path("id").asText().equals(id);}catch(Exception e){throw new IllegalStateException("Invalid budget audit evidence",e);}}
  @Transactional(readOnly=true) public List<LedgerAudit> history(String id){var p=period(id);return audits.findAll().stream().filter(h->h.actorId.equals(AccessContext.userId())&&h.action.startsWith("BUDGET_")&&concerns(h,p.id())).sorted(Comparator.comparing(h->h.recordedAt)).toList();}
}
