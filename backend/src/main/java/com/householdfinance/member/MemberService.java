package com.householdfinance.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.AccessContext;
import com.householdfinance.forecast.*;
import com.householdfinance.ledger.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Member decisions are overlays, never instructions to move money or change actuals. */
@Service
@Transactional(readOnly=true)
public class MemberService {
  public record Publication(Long revision, Long forecastRevision, Boolean acknowledged) {}
  public record Board(String publisherId, String publisherName, long forecastRevision,
      ForecastService.Stored forecast, Instant publishedAt) {}
  public record Purchase(String title, String category, long amountMinor, LocalDate date,
      String state, String requestDecision, boolean sharedRequest, String reviewerId, Set<String> sourceIds,
      String accountId, String entryId, String decisionReason) {}
  public record NewPurchase(String id, String title, String category, String amountEuro,
      LocalDate date, Boolean sharedRequest, String reportToken, Boolean acknowledged) {}
  public record Change(Long revision, String action, String accountId, String entryId, String reason, String reportToken) {}
  public record PurchaseView(String id, String ownerId, long revision, Purchase purchase,
      Long actualMinor, Long varianceMinor, boolean matchValid, String matchWarning,
      boolean canDecide, String assessment) {}
  public record Preferences(List<String> categories, String dashboard, boolean notifications, Instant seenAt) {}
  public record PreferenceInput(Long revision, List<String> categories, String dashboard,
      Boolean notifications, Boolean markSeen) {}
  public record PreferenceView(long revision, Preferences values) {}
  public record Event(String purchaseId, String actor, String action, Instant at, Purchase snapshot,
      String evidenceToken, String evidenceState) {}
  public record Notification(String id, String purchaseId, String action, Instant at) {}
  public record Report(LocalDate asOf, String scope, LedgerService.Position position,
      Long recordedCashMinor, String evidenceState, Long spendingRoomMinor, Long commitmentsMinor, Long reserveMinor,
      long purchaseReservationsMinor, Board board, long boardRevision, boolean canPublish,
      String token, List<String> warnings, String trendState) {}
  public record Workspace(Report report, List<PurchaseView> purchases, PreferenceView preferences,
      List<Notification> notifications) {}
  private final MemberRepository records;
  private final LedgerService ledger;
  private final LedgerAccountRepository accounts;
  private final LedgerEntryRepository entries;
  private final ForecastService forecasts;
  private final ForecastPlanRepository plans;
  private final ObjectMapper json;
  private final JdbcTemplate jdbc;
  public MemberService(MemberRepository records, LedgerService ledger, LedgerAccountRepository accounts,
      LedgerEntryRepository entries, ForecastService forecasts, ForecastPlanRepository plans,
      ObjectMapper json, JdbcTemplate jdbc) {
    this.records=records;this.ledger=ledger;this.accounts=accounts;this.entries=entries;
    this.forecasts=forecasts;this.plans=plans;this.json=json;this.jdbc=jdbc;
  }
  private LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Dublin"));}
  private static void check(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
  private static void conflict(boolean ok){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT,"Evidence or decision changed; refresh before trying again");}
  private static long safe(long value){check(value>=-9_007_199_254_740_991L&&value<=9_007_199_254_740_991L,"Member totals exceed the exact reporting range");return value;}
  private static String text(String v,int limit){check(v!=null&&!v.isBlank()&&v.strip().length()<=limit,"Complete the text within the displayed limit");return v.strip();}
  private <T>T read(MemberRecord r,Class<T> type){try{return json.readValue(r.payload,type);}catch(Exception e){throw new IllegalArgumentException("Saved member record needs verified recovery");}}
  private MemberRecord write(String id,String kind,String owner,Object data,MemberRecord existing){
    var r=existing==null?new MemberRecord():existing;r.id=id;r.kind=kind;r.ownerId=owner;r.updatedAt=Instant.now();
    try{r.payload=json.writeValueAsString(data);}catch(Exception e){throw new IllegalArgumentException("Cannot save member record");}
    return records.saveAndFlush(r);
  }
  private long revision(MemberRecord r){return r==null?0:r.version+1;}
  private boolean sharedSources(Set<String> ids){return !ids.isEmpty()&&ids.stream().allMatch(id->accounts.findById(id).filter(a->a.shared&&AccessContext.reads(a.ownerId,a.shared)).isPresent());}
  private boolean visible(Purchase p,MemberRecord r){
    boolean entitled=AccessContext.owns(r.ownerId)|| (p.sharedRequest()&&AccessContext.userId().equals(p.reviewerId()));
    return entitled&&(!p.sharedRequest()||sharedSources(p.sourceIds()));
  }
  private MemberRecord purchase(String id){AccessContext.requireRead();return records.findById(id).filter(r->r.kind.equals("PURCHASE")&&visible(read(r,Purchase.class),r)).orElseThrow(()->new IllegalArgumentException("Purchase unavailable"));}
  private PreferenceView preferences(){
    var r=records.findById("prefs:"+AccessContext.userId()).orElse(null);
    return new PreferenceView(revision(r),r==null?new Preferences(List.of("Everyday","Home","Other"),"DETAIL",true,Instant.EPOCH):read(r,Preferences.class));
  }
  private LedgerEntry match(Purchase p){
    if(p.entryId()==null||p.accountId()==null)return null;
    if(accounts.findById(p.accountId()).filter(a->a.shared&&a.kind.equals("CASH")&&AccessContext.reads(a.ownerId,a.shared)).isEmpty())return null;
    return entries.findById(p.entryId()).filter(e->e.accountId.equals(p.accountId())&&e.status.equals("BOOKED")&&e.kind.equals("ACTIVITY")&&e.amountMinor<0&&!e.date.isAfter(today())).orElse(null);
  }
  private PurchaseView view(MemberRecord r,Report report){
    var p=read(r,Purchase.class);var m=match(p);Long actual=m==null?null:-m.amountMinor;
    String assessment=report.spendingRoomMinor()==null?"UNKNOWN":p.date().isAfter(report.board().forecast().endDate())?"OUTSIDE_HORIZON":
      report.spendingRoomMinor()-(p.requestDecision().equals("PENDING")&&!AccessContext.owns(r.ownerId)?p.amountMinor():0)>=0?"WITHIN_REVIEWED_ROOM":"EXCEEDS_REVIEWED_ROOM";
    return new PurchaseView(r.id,r.ownerId,revision(r),p,actual,actual==null?null:actual-p.amountMinor(),m!=null,
      p.entryId()!=null&&m==null?"Matched activity changed or access was withdrawn; amount remains reserved until reviewed":null,
      p.requestDecision().equals("PENDING")&&!p.state().equals("CANCELLED")&&AccessContext.actor().edits()&&AccessContext.userId().equals(p.reviewerId()),assessment);
  }
  private String token(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  public Workspace get(){
    AccessContext.requireRead();var report=report();var prefs=preferences();
    var purchases=records.findByKindOrderByUpdatedAtDesc("PURCHASE").stream().filter(r->visible(read(r,Purchase.class),r)).map(r->view(r,report)).toList();
    List<Notification> notices=new ArrayList<>();
    if(prefs.values().notifications())for(var r:records.findByKindOrderByUpdatedAtDesc("EVENT")){
      var e=read(r,Event.class);if(!e.at().isAfter(prefs.values().seenAt()))continue;
      var p=records.findById(e.purchaseId()).orElse(null);
      if(p!=null&&visible(read(p,Purchase.class),p)&&(!e.snapshot().sharedRequest()||sharedSources(e.snapshot().sourceIds())))notices.add(new Notification(r.id,e.purchaseId(),e.action(),e.at()));
      if(notices.size()==30)break;
    }
    return new Workspace(report,purchases,prefs,List.copyOf(notices));
  }
  public Report report(){
    AccessContext.requireRead();var pos=ledger.sharedPosition(today());
    var raw=records.findById("board").orElse(null);var b=raw==null?null:read(raw,Board.class);
    if(b!=null&&!sharedSources(b.forecast().baseline().revisions().keySet()))b=null;
    var warnings=new ArrayList<String>();warnings.add("Shared accounts only. Private finances, unrecorded bills and other members' private purchases are excluded. This is not permission to spend.");
    warnings.add("Expected and uncertain income are excluded from conservative spending room. Pending cash debits are reserved once. Credit limits are never cash.");
    String state="NO_PUBLISHED_FORECAST";Long room=null,out=null,reserve=null;
    var cash=pos.accounts().stream().filter(a->a.kind().equals("CASH")).toList();
    Map<String,Long> revisions=new TreeMap<>();cash.forEach(a->revisions.put(a.id(),a.revision()));
    long reservations=0;var included=new ArrayList<String>();
    for(var r:records.findByKindOrderByUpdatedAtDesc("PURCHASE")){
      var p=read(r,Purchase.class);
      boolean own=AccessContext.owns(r.ownerId)&&visible(p,r);
      boolean approved=p.sharedRequest()&&p.requestDecision().equals("APPROVED")&&!p.state().equals("CANCELLED")&&sharedSources(p.sourceIds());
      if(!own&&!approved)continue;
      included.add(r.id+":"+revision(r));
      if(!Set.of("CANCELLED","DECLINED").contains(p.state())&&match(p)==null)reservations=safe(Math.addExact(reservations,p.amountMinor()));
    }
    if(b!=null){
      var f=b.forecast();var source=plans.findById(b.publisherId().equals("owner")?"household":b.publisherId()).orElse(null);
      boolean publisher=jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE ID=? AND STATE='ACTIVE' AND (ROLES='EDITOR' OR ROLES LIKE 'EDITOR,%' OR ROLES LIKE '%,EDITOR' OR ROLES LIKE '%,EDITOR,%')",Integer.class,b.publisherId())==1;
      state=!f.baseline().date().equals(today())||f.endDate().isBefore(today())?"STALE":
        source==null||source.version+1!=b.forecastRevision()||!publisher||!revisions.equals(f.baseline().revisions())||cash.stream().anyMatch(a->!a.reconciled())?"SOURCE_CHANGED":"REVIEWED";
      out=f.events().stream().filter(e->e.direction().equals("EXPENSE")).mapToLong(ForecastService.Event::amountMinor).reduce(0,Math::addExact);
      reserve=f.reserveMinor();
      if(state.equals("REVIEWED"))room=safe(Math.subtractExact(Math.subtractExact(Math.subtractExact(Math.subtractExact(pos.cashMinor(),pos.cashPendingDebitMinor()),out),reserve),reservations));
    }
    if(!state.equals("REVIEWED"))warnings.add("Spending room is unknown. An editor must publish today's reconciled shared-source forecast and review changed evidence.");
    warnings.add("Purchase reservations include your unmatched plans and approved shared requests, including bought items awaiting a booked-activity match. Amounts are advisory; no purchase is blocked.");
    String fingerprint=token(List.of(today().toString(),AccessContext.userId(),AccessContext.actor().revision(),revisions,pos.accounts().stream().map(a->a.id()+":"+a.revision()+":"+a.reconciled()).toList(),raw==null?0:revision(raw),state,included));
    return new Report(today(),"SHARED_ONLY",pos,cash.isEmpty()?null:pos.cashMinor(),state,room,out,reserve,reservations,b,b==null?0:revision(raw),AccessContext.actor().edits()&&(raw==null||AccessContext.owns(raw.ownerId)),fingerprint,List.copyOf(warnings),"UNAVAILABLE: complete categorised period coverage has not been established");
  }
  @Transactional public Workspace publish(Publication input){
    AccessContext.requireEdit();var r=records.findById("board").orElse(null);
    check(r==null||AccessContext.owns(r.ownerId),"Only the publishing editor can replace this shared forecast");
    check(input!=null&&input.revision()!=null&&input.forecastRevision()!=null&&Boolean.TRUE.equals(input.acknowledged()),"Explicit publication consent and revisions required");conflict(input.revision()==revision(r));
    var view=forecasts.get().plan();check(view!=null&&view.currentDecisionReady()&&Set.of("LEDGER","SHARED_LEDGER").contains(view.inputs().baseline().mode()),"Save today's reconciled shared-source ledger forecast first");conflict(input.forecastRevision()==view.revision());
    var p=ledger.sharedPosition(today());Map<String,Long> refs=new TreeMap<>();p.accounts().stream().filter(a->a.kind().equals("CASH")).forEach(a->refs.put(a.id(),a.revision()));
    check(sharedSources(refs.keySet())&&refs.equals(view.inputs().baseline().revisions()),"Every forecast cash source must be explicitly shared; private forecasts cannot be published");
    write("board","BOARD",AccessContext.userId(),new Board(AccessContext.userId(),AccessContext.actor().username(),view.revision(),view.inputs(),Instant.now()),r);
    return get();
  }
  @Transactional public Workspace create(NewPurchase input){
    AccessContext.requireRead();check(input!=null&&input.sharedRequest()!=null&&Boolean.TRUE.equals(input.acknowledged()),"Confirm this additional cost is not already in published bills, pending activity or another purchase");var report=report();
    String id;try{id=UUID.fromString(input.id()).toString();}catch(Exception e){throw new IllegalArgumentException("Stable purchase UUID required");}
    String title=text(input.title(),100),category=text(input.category(),40);
    check(input.amountEuro()!=null&&input.amountEuro().matches("(?:0|[1-9][0-9]{0,9})(?:\\.[0-9]{1,2})?"),"Use positive exact EUR text with at most two decimal places");
    long amount=new BigDecimal(input.amountEuro()).movePointRight(2).longValueExact();check(amount>0,"Purchase amount must be positive");
    var existing=records.findById(id).orElse(null);
    if(existing!=null){
      conflict(existing.kind.equals("PURCHASE")&&AccessContext.owns(existing.ownerId));var prior=read(existing,Purchase.class);
      conflict(Objects.equals(prior.title(),title)&&Objects.equals(prior.category(),category)&&Objects.equals(prior.date(),input.date())&&prior.sharedRequest()==input.sharedRequest()&&prior.amountMinor()==amount);
      return get();
    }
    conflict(Objects.equals(input.reportToken(),report.token()));
    check(records.findByKindOrderByUpdatedAtDesc("PURCHASE").stream().filter(r->AccessContext.owns(r.ownerId)).count()<200,"Local purchase limit reached");
    check(input.date()!=null&&!input.date().isBefore(today())&&!input.date().isAfter(today().plusDays(365)),"Choose a purchase date within the next year");
    var b=report.board();check(!input.sharedRequest()||(b!=null&&report.evidenceState().equals("REVIEWED")&&!b.publisherId().equals(AccessContext.userId())),"A shared request needs a current published forecast and a different publishing editor");
    var p=new Purchase(title,category,amount,input.date(),input.sharedRequest()?"REQUESTED":"PLANNED",input.sharedRequest()?"PENDING":"NONE",input.sharedRequest(),input.sharedRequest()?b.publisherId():null,input.sharedRequest()?b.forecast().baseline().revisions().keySet():Set.of(),null,null,"");
    var r=write(id,"PURCHASE",AccessContext.userId(),p,null);event(r,p,"CREATED");return get();
  }
  private void event(MemberRecord r,Purchase p,String action){var report=report();write(UUID.randomUUID().toString(),"EVENT",r.ownerId,new Event(r.id,AccessContext.actor().username(),action,Instant.now(),p,report.token(),report.evidenceState()),null);}
  @Transactional public Workspace change(String id,Change input){
    AccessContext.requireRead();var r=purchase(id);var p=read(r,Purchase.class);
    check(input!=null&&input.revision()!=null&&input.action()!=null,"Complete decision and revision required");conflict(input.revision()==revision(r)&&Objects.equals(input.reportToken(),report().token()));
    String state=p.state(),decision=p.requestDecision(),account=p.accountId(),entry=p.entryId(),reason=p.decisionReason();
    if(Set.of("APPROVE","DECLINE").contains(input.action())){
      AccessContext.requireEdit();check(p.sharedRequest()&&AccessContext.userId().equals(p.reviewerId())&&!AccessContext.owns(r.ownerId)&&decision.equals("PENDING")&&!state.equals("CANCELLED"),"Only the receiving editor can decide a pending request");
      check(report().evidenceState().equals("REVIEWED"),"Review and republish current source evidence before deciding");reason=text(input.reason(),200);decision=input.action().equals("APPROVE")?"APPROVED":"DECLINED";if(!state.equals("BOUGHT"))state=decision;
    }else{
      check(AccessContext.owns(r.ownerId),"Only the purchase owner can record routine actions");
      switch(input.action()){
        case "CANCEL" -> {check(Set.of("PLANNED","REQUESTED","APPROVED","DECLINED").contains(state),"This purchase cannot be cancelled");state="CANCELLED";}
        case "BOUGHT" -> {check(Set.of("PLANNED","REQUESTED","APPROVED","DECLINED").contains(state),"This purchase is already bought or cancelled");state="BOUGHT";}
        case "MATCH" -> {
          check(state.equals("BOUGHT"),"Mark bought before matching actual activity");account=text(input.accountId(),36);entry=text(input.entryId(),36);
          var candidate=new Purchase(p.title(),p.category(),p.amountMinor(),p.date(),state,decision,p.sharedRequest(),p.reviewerId(),p.sourceIds(),account,entry,reason);
          check(match(candidate)!=null,"Choose a permitted shared cash account's booked outgoing activity");
          final String matched=entry;
          check(records.findByKindOrderByUpdatedAtDesc("PURCHASE").stream().filter(other->!other.id.equals(id)).map(other->read(other,Purchase.class)).noneMatch(other->matched.equals(other.entryId())),"This activity already has a purchase match");
        }
        case "UNMATCH" -> {check(state.equals("BOUGHT")&&entry!=null,"No actual match to remove");account=null;entry=null;}
        default -> throw new IllegalArgumentException("Unknown purchase action");
      }
    }
    var next=new Purchase(p.title(),p.category(),p.amountMinor(),p.date(),state,decision,p.sharedRequest(),p.reviewerId(),p.sourceIds(),account,entry,reason);
    write(id,"PURCHASE",r.ownerId,next,r);event(r,next,input.action());return get();
  }
  public List<Event> history(String id){
    purchase(id);return records.findByKindOrderByUpdatedAtDesc("EVENT").stream().map(r->read(r,Event.class)).filter(e->e.purchaseId().equals(id)&&(!e.snapshot().sharedRequest()||sharedSources(e.snapshot().sourceIds()))).toList();
  }
  @Transactional public Workspace preferences(PreferenceInput input){
    AccessContext.requireRead();check(input!=null&&input.revision()!=null&&input.categories()!=null&&input.categories().size()<=20&&input.notifications()!=null&&input.markSeen()!=null&&Set.of("COMPACT","DETAIL").contains(Objects.toString(input.dashboard(),"")),"Complete personal preferences required");
    var r=records.findById("prefs:"+AccessContext.userId()).orElse(null);conflict(input.revision()==revision(r));
    var cats=input.categories().stream().map(v->text(v,40)).distinct().toList();check(!cats.isEmpty(),"Keep at least one purchase category");
    write("prefs:"+AccessContext.userId(),"PREFERENCES",AccessContext.userId(),new Preferences(cats,input.dashboard(),input.notifications(),input.markSeen()?Instant.now():preferences().values().seenAt()),r);return get();
  }
}
