package com.householdfinance.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.AccessContext;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All review decisions remain local, explicit and version checked. */
@Service
public class ActivityReviewService {
  public record Category(String id, long revision, String name, String parentId, boolean retired, String budgetEuro) {}
  public record CategoryInput(Long revision, String name, String parentId, boolean retired, String budgetEuro, String mergeInto, String reason) {}
  public record Classification(Long revision, String categoryId, String state, String reason, Boolean offerRule) {}
  public record Rule(String id, long revision, String description, String categoryId, boolean approved) {}
  public record RuleApproval(Long revision, boolean approved, String reason) {}
  public record Link(Long revision, String otherId, Long otherRevision, String type, String reason) {}
  public record Reopen(Long revision, String reason) {}
  public record Suggestion(String categoryId, String confidence, String explanation) {}
  public record Item(String accountId, String accountName, long revision, LedgerEntry entry, List<String> issues, String risk, Suggestion suggestion) {}
  public record Guide(LocalDate throughDate, int unresolved, List<String> checks, long monthSpendingMinor, long budgetMinor, List<LedgerReviewRecord> completedReviews) {}
  public record Completion(LocalDate throughDate, String reason, boolean acknowledged, Long expectedAuditCount) {}
  private final LedgerAccountRepository accounts;
  private final LedgerEntryRepository entries;
  private final LedgerAuditRepository audits;
  private final LedgerReviewRepository records;
  private final LedgerService ledger;
  private final ObjectMapper json;
  @org.springframework.beans.factory.annotation.Autowired private com.householdfinance.forecast.ForecastService forecast;
  public ActivityReviewService(LedgerAccountRepository a, LedgerEntryRepository e, LedgerAuditRepository h, LedgerReviewRepository r, LedgerService l, ObjectMapper j) {
    accounts=a;entries=e;audits=h;records=r;ledger=l;json=j;
  }
  private void check(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
  private String text(String value,int max){check(value!=null&&!value.isBlank()&&value.strip().length()<=max,"A bounded name or reason is required");return value.strip();}
  private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Cannot save review evidence",e);}}
  private <T> T read(LedgerReviewRecord r,Class<T> type){try{return json.readValue(r.payload,type);}catch(Exception e){throw new IllegalStateException("Review evidence is invalid",e);}}
  private LedgerAccount account(String id){return accounts.findById(id).filter(a -> AccessContext.reads(a.ownerId,a.shared)).orElseThrow(()->new IllegalArgumentException("Unknown ledger account"));}
  private LedgerEntry entry(String id){var e=entries.findById(id).orElseThrow(()->new IllegalArgumentException("Unknown activity"));account(e.accountId);return e;}
  private void revision(LedgerAccount a,Long revision){AccessContext.requireEdit();check(AccessContext.owns(a.ownerId),"Only the account owner may review this account");check(revision!=null&&a.revision==revision,"Account changed; reload and review again");}
  private void protect(LedgerAccount a,LedgerEntry e){check(a.reconciledDate==null||e.date.isAfter(a.reconciledDate),"Reopen reconciliation with a reason before changing protected activity");}
  private void changed(LedgerAccount a){a.revision++;accounts.saveAndFlush(a);}
  private void audit(String account,String action,String reason,Object before,Object after){var h=new LedgerAudit();h.id=UUID.randomUUID().toString();h.accountId=account;h.action=action;h.source=text(reason,200);h.recordedAt=Instant.now();h.beforeJson=encode(before);h.afterJson=encode(after);audits.save(h);}
  private List<LedgerReviewRecord> owned(String kind){AccessContext.requireRead();return records.findAll().stream().filter(r->r.kind.equals(kind)&&AccessContext.owns(r.ownerId)).toList();}
  private LedgerReviewRecord ownedRecord(String id,String kind){return owned(kind).stream().filter(r->r.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown review record"));}
  private LedgerReviewRecord save(String id,String kind,Object payload){var r=id==null?new LedgerReviewRecord():ownedRecord(id,kind);if(id==null){r.id=UUID.randomUUID().toString();r.ownerId=AccessContext.userId();r.kind=kind;}else r.revision++;r.payload=encode(payload);return records.save(r);}
  @Transactional(readOnly=true) public List<Category> categories(){return owned("CATEGORY").stream().map(r->read(r,Category.class)).sorted(Comparator.comparing(Category::name)).toList();}
  @Transactional(readOnly=true) public List<Rule> rules(){return owned("RULE").stream().map(r->read(r,Rule.class)).toList();}
  private Category category(String id){return categories().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown category"));}
  private Category active(String id){var c=category(id);check(!c.retired(),"Choose an active category");return c;}
  @Transactional public Category category(String id,CategoryInput input){
    AccessContext.requireEdit();check(input!=null,"Category required");String reason=text(input.reason(),200);
    Category before=id==null?null:category(id);
    check(before==null||Objects.equals(input.revision(),before.revision()),"Category changed; reload");
    String name=text(input.name(),80);long budget=LedgerService.money(input.budgetEuro());check(budget>=0,"Budget must be nonnegative");
    if(input.parentId()!=null){var parent=active(input.parentId());check(parent.parentId()==null&&!parent.id().equals(id),"Use one subcategory level");check(id==null||categories().stream().noneMatch(c->Objects.equals(c.parentId(),id)),"Move child categories first");}
    check(categories().stream().noneMatch(c->!c.id().equals(id)&&c.name().equalsIgnoreCase(name)&&Objects.equals(c.parentId(),input.parentId())),"Category already exists at this level");
    check(before!=null||categories().size()<200,"Category limit reached");
    if(input.mergeInto()!=null){
      check(id!=null&&!id.equals(input.mergeInto()),"Choose a different merge destination");active(input.mergeInto());
      check(categories().stream().noneMatch(c->Objects.equals(c.parentId(),id)),"Move child categories before merging");
      var affected=entries.findAll().stream().filter(e->id.equals(e.categoryId)).toList();
      // Stable IDs permit rename/retire without changing historical facts. Merge is explicit and cannot cross protected dates.
      for(var e:affected){var a=account(e.accountId);check(AccessContext.owns(a.ownerId),"Category is referenced outside the owned scope");protect(a,e);}
      Set<String> touched=new HashSet<>();for(var e:affected){var old=encode(e);e.categoryId=input.mergeInto();audit(e.accountId,"CATEGORY_MERGE",reason,old,e);touched.add(e.accountId);}
      for(String a:touched)changed(account(a));
      for(var r:owned("RULE")){var rule=read(r,Rule.class);if(rule.categoryId().equals(id)){r.revision++;r.payload=encode(new Rule(r.id,r.revision,rule.description(),input.mergeInto(),false));records.save(r);}}
    }
    if(input.retired()&&id!=null)check(categories().stream().noneMatch(c->Objects.equals(c.parentId(),id)&&!c.retired()),"Retire or move active child categories first");
    var r= id==null?new LedgerReviewRecord():ownedRecord(id,"CATEGORY");if(id==null){r.id=UUID.randomUUID().toString();r.ownerId=AccessContext.userId();r.kind="CATEGORY";}else r.revision++;
    var result=new Category(r.id,r.revision,name,input.parentId(),input.retired()||input.mergeInto()!=null,input.budgetEuro());r.payload=encode(result);records.save(r);
    audit(AccessContext.publicationId(),"CATEGORY",reason,before,result);return result;
  }
  public record Revision(Long revision) {}
  public record Merchant(Long revision, String label) {}
  @Transactional(readOnly=true)
  public LedgerEntry suggestionEntry(String id, Long expectedRevision) {
    var e = entry(id);
    revision(account(e.accountId), expectedRevision);
    return e;
  }
  @Transactional
  public void merchant(String id, Merchant input) {
    var a = accounts.lockForActivity(id).orElseThrow(() -> new IllegalArgumentException("Unknown activity"));
    var e = entry(id);
    revision(a, input.revision());
    protect(a, e);
    var label = text(input.label(), 80);
    check(label.chars().noneMatch(Character::isISOControl), "Use a single-line merchant label");
    var before = encode(e);
    e.reviewedMerchant = label;
    audit(a.id, "REVIEW_MERCHANT", "Owner reviewed " + LocalClassifier.MODEL + " merchant suggestion", before, e);
    changed(a);
  }
  @Transactional public void classify(String id,Classification input){
    check(input!=null,"Review required");var a=accounts.lockForActivity(id).orElseThrow(() -> new IllegalArgumentException("Unknown activity"));revision(a,input.revision());var e=entry(id);protect(a,e);
    var c=active(input.categoryId());check(Set.of("CONFIRMED","PROVISIONAL").contains(Objects.toString(input.state(),"")),"Choose confirmed or provisional");
    var before=encode(e);e.categoryId=c.id();e.reviewState=input.state();audit(a.id,"CLASSIFY",input.reason(),before,e);changed(a);
    if(Boolean.TRUE.equals(input.offerRule())){
      check(e.reviewState.equals("CONFIRMED"),"Confirm the correction before offering a rule");check(rules().size()<200,"Rule limit reached");
      var r=save(null,"RULE",null);r.payload=encode(new Rule(r.id,0,e.description,c.id(),false));records.save(r);
      audit(a.id,"OFFER_RULE",input.reason(),null,read(r,Rule.class));
    }
  }
  @Transactional public Rule approveRule(String id,RuleApproval input){
    AccessContext.requireEdit();check(input!=null,"Explicit rule choice required");var r=ownedRecord(id,"RULE");var before=read(r,Rule.class);
    check(Objects.equals(input.revision(),r.revision),"Rule changed; reload");if(input.approved())active(before.categoryId());
    var result=new Rule(id,++r.revision,before.description(),before.categoryId(),input.approved());r.payload=encode(result);records.save(r);audit(AccessContext.publicationId(),"RULE_APPROVAL",input.reason(),before,result);return result;
  }
  @Transactional(readOnly=true)
  public Suggestion categoryPrefill(LedgerEntry entry, List<Category> categories) {
    account(entry.accountId);
    var history = entries.findByAccountIdOrderByDateAscReferenceAsc(entry.accountId);
    return new CategoryPrefill(rules(), categories, history).suggest(entry);
  }
  private List<String> issues(LedgerEntry e){
    var list=new ArrayList<String>();if(!e.reviewState.equals("CONFIRMED"))list.add("PROVISIONAL");
    if(e.categoryId==null&&e.kind.equals("ACTIVITY")&&!LedgerService.isTransferRelationship(e))list.add("CATEGORY");
    if(e.status.equals("PENDING"))list.add("PENDING");
    if(e.kind.equals("TRANSFER")&&e.linkedEntryId==null)list.add("COUNTERPART");return list;
  }
  @Transactional(readOnly=true) public List<Item> queue(String accountId,LocalDate from,LocalDate to,String issue,String risk){
    AccessContext.requireRead();check(from!=null&&to!=null&&!from.isAfter(to),"Choose a valid date range");
    var result=new ArrayList<Item>();
    var localRules=rules();var localCategories=categories();
    for(var a:accounts.findAll())if(AccessContext.owns(a.ownerId)&&(accountId==null||a.id.equals(accountId))){
      var history = entries.findByAccountIdOrderByDateAscReferenceAsc(a.id);
      var prefill = new CategoryPrefill(localRules, localCategories, history);
      for(var e:history)if(!e.status.equals("VOID")&&!e.date.isBefore(from)&&!e.date.isAfter(to)){
        var problems=issues(e);String level=problems.contains("COUNTERPART")||Math.abs(e.amountMinor)>=100000?"HIGH":"NORMAL";
        if((issue==null||problems.contains(issue))&&(risk==null||risk.equals(level)))result.add(new Item(a.id,a.name,a.revision,e,problems,level,prefill.suggest(e)));
      }
    }
    return result.stream().sorted(Comparator.comparing((Item i)->!i.risk().equals("HIGH")).thenComparing(i->i.entry().date)).toList();
  }
  @Transactional public void link(String id,Link input){
    check(input!=null,"Link choice required");var e=entry(id);var a=account(e.accountId);revision(a,input.revision());protect(a,e);
    check(e.status.equals("BOOKED")&&!e.kind.equals("ADJUSTMENT"),"Link booked non-adjustment activity only");
    var other=entry(input.otherId());var b=account(other.accountId);revision(b,input.otherRevision());protect(b,other);
    check(!e.id.equals(other.id)&&e.linkedEntryId==null&&other.status.equals("BOOKED")&&!other.kind.equals("ADJUSTMENT"),"Choose a booked counterpart that is not a reconciliation adjustment");
    String type=Objects.toString(input.type(),"");check(Set.of("TRANSFER","CARD_SETTLEMENT","REFUND","REVERSAL").contains(type),"Unknown relationship");
    var before=List.of(encode(e),encode(other));
    if(type.equals("TRANSFER")||type.equals("CARD_SETTLEMENT")){
      check(!a.id.equals(b.id)&&other.linkedEntryId==null&&e.amountMinor==-other.amountMinor,"Transfer requires equal opposite amounts in two accounts");
      check(!a.kind.equals("DEBT")||!b.kind.equals("DEBT"),"Choose at least one cash account");
      if(type.equals("CARD_SETTLEMENT"))check(!a.kind.equals(b.kind),"Card settlement requires cash and debt accounts");
      check(entries.findAll().stream().noneMatch(r->e.id.equals(r.linkedEntryId)||other.id.equals(r.linkedEntryId)),"Activity already has dependent links");
      other.linkedEntryId=e.id;other.linkType=type;other.reviewState="CONFIRMED";
    } else {
      check(a.id.equals(b.id)&&other.linkedEntryId==null&&other.kind.equals("ACTIVITY")&&Long.signum(e.amountMinor)==-Long.signum(other.amountMinor)&&!e.date.isBefore(other.date),"Refund/reversal must oppose an original activity in the same account and follow its date");
      if(type.equals("REFUND"))check(other.amountMinor<0,"A refund must reference an original debit");
      long refunded=entries.findByAccountIdOrderByDateAscReferenceAsc(a.id).stream().filter(r->other.id.equals(r.linkedEntryId)&&!r.status.equals("VOID")).mapToLong(r->Math.abs(r.amountMinor)).reduce(0,Math::addExact);
      check(Math.addExact(refunded,Math.abs(e.amountMinor))<=Math.abs(other.amountMinor),"Cumulative refunds/reversals exceed the original amount");
      check(entries.findAll().stream().noneMatch(r->e.id.equals(r.linkedEntryId)),"Activity already has dependent links");
      e.categoryId=other.categoryId;
    }
    e.linkedEntryId=other.id;e.linkType=type;e.reviewState="CONFIRMED";
    audit(a.id,"LINK",input.reason(),before,List.of(e,other));if(!a.id.equals(b.id))audit(b.id,"LINK",input.reason(),before,List.of(e,other));changed(a);if(!a.id.equals(b.id))changed(b);
  }
  @Transactional public void unlink(String id,Link input){
    check(input!=null,"Unlink reason required");var e=entry(id);var a=account(e.accountId);revision(a,input.revision());protect(a,e);
    check(e.linkedEntryId!=null&&e.linkedEntryId.equals(input.otherId()),"Reload the existing relationship");var other=entry(e.linkedEntryId);var b=account(other.accountId);revision(b,input.otherRevision());protect(b,other);
    var before=List.of(encode(e),encode(other));boolean mutual=e.id.equals(other.linkedEntryId);
    e.linkedEntryId=null;e.linkType=null;e.reviewState="PROVISIONAL";
    if(mutual){other.linkedEntryId=null;other.linkType=null;other.reviewState="PROVISIONAL";}
    audit(a.id,"UNLINK",input.reason(),before,List.of(e,other));changed(a);if(!a.id.equals(b.id)){audit(b.id,"UNLINK",input.reason(),before,List.of(e,other));changed(b);}
  }
  @Transactional public void reopen(String id,Reopen input){
    check(input!=null,"Reopening reason required");var a=account(id);revision(a,input.revision());check(a.reconciledDate!=null,"No protected reconciliation to reopen");
    audit(id,"REOPEN",input.reason(),Map.of("throughDate",a.reconciledDate,"balanceMinor",a.reconciledBalance,"source",a.reconciledSource),"Reconciliation reopened; corrections require a new statement review");
    a.reconciledDate=null;a.reconciledRevision=null;a.reconciledBalance=null;a.reconciledSource=null;changed(a);
  }
  @Transactional(readOnly=true) public Guide guide(LocalDate date){
    AccessContext.requireRead();check(date!=null&&!date.isAfter(LocalDate.now(ZoneId.of("Europe/Dublin"))),"Review date must be today or earlier");
    var p=ledger.position(date);var queue=queue(null,LocalDate.of(1900,1,1),date,null,null);int unresolved=(int)queue.stream().filter(i->!i.issues().isEmpty()).count();
    var checks=new ArrayList<String>();if(p.accounts().isEmpty())checks.add("Add source-backed accounts before completing a review");
    for(var a:p.accounts()){
      if(!a.owned())continue;
      if(!a.reconciled())checks.add(a.name()+": compare the statement balance through "+date+" and resolve any difference");
      var latest=audits.findByAccountIdOrderByRecordedAtDesc(a.id()).stream().filter(h->Set.of("IMPORT","REVIEW_IMPORT","MANUAL").contains(h.action)).findFirst();
      checks.add(a.name()+": last local activity review "+latest.map(h->h.recordedAt.toString()).orElse("not recorded")+"; verify source freshness separately");
    }
    if(unresolved>0)checks.add(unresolved+" unresolved activity items remain open");
    long spending=queue.stream().filter(i->!i.entry().date.isBefore(date.withDayOfMonth(1))).mapToLong(i->LedgerService.spendingContribution(i.entry())).sum();
    long budget=categories().stream().filter(c->!c.retired()&&c.parentId()==null).mapToLong(c->LedgerService.money(c.budgetEuro())).sum();
    checks.add(budget==0?"No category budget recorded; budget variance is unknown":"Month net spending less category budget: €"+java.math.BigDecimal.valueOf(spending-budget,2).toPlainString());
    var plan=forecast.get().plan();
    if(plan==null)checks.add("Upcoming risk unknown: no visible forecast. Record upcoming bills and income in Plan ahead");
    else {
      checks.add("Upcoming forecast evidence: "+plan.evidenceState()+"; decision-ready: "+plan.currentDecisionReady());
      checks.addAll(plan.warnings());
      checks.add("Forecast shortfall date: "+Objects.toString(plan.projection().shortfallDate(),"none within the saved horizon")+"; reserve breach: "+Objects.toString(plan.projection().reserveBreachDate(),"none within the saved horizon"));
    }
    return new Guide(date,unresolved,List.copyOf(checks),spending,budget,owned("REVIEW"));
  }
  @Transactional public LedgerReviewRecord complete(Completion input){
    AccessContext.requireEdit();check(input!=null&&input.acknowledged(),"Acknowledge every guided review check");
    long count=visibleAuditCount();check(Objects.equals(input.expectedAuditCount(),count),"Evidence changed; reload guided review");
    var guide=guide(input.throughDate());check(guide.unresolved()==0,"Resolve activity issues before completing review");
    var p=ledger.position(input.throughDate());check(!p.accounts().isEmpty()&&p.accounts().stream().filter(LedgerService.AccountView::owned).allMatch(LedgerService.AccountView::reconciled),"Reconcile all owned accounts for the review date");
    var r=save(null,"REVIEW",Map.of("guide",guide.checks(),"throughDate",input.throughDate(),"reason",text(input.reason(),200),"actor",AccessContext.userId(),"recordedAt",Instant.now(),"revisions",p.accounts().stream().filter(LedgerService.AccountView::owned).map(a->Map.of("id",a.id(),"revision",a.revision())).toList()));
    audit(AccessContext.publicationId(),"COMPLETE_REVIEW",input.reason(),null,r);return r;
  }
  @Transactional(readOnly=true) public long visibleAuditCount(){AccessContext.requireRead();Set<String> ids=new HashSet<>();accounts.findAll().stream().filter(a->AccessContext.owns(a.ownerId)).forEach(a->ids.add(a.id));ids.add(AccessContext.publicationId());return audits.findAll().stream().filter(h->ids.contains(h.accountId)).count();}
}
