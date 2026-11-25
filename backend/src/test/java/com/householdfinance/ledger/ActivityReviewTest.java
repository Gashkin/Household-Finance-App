package com.householdfinance.ledger;
import com.householdfinance.access.*;
import com.householdfinance.workspace.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"workspace.password=synthetic-review-password","spring.datasource.url=jdbc:h2:mem:activity-review;DB_CLOSE_DELAY=-1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class ActivityReviewTest {
 @Autowired LedgerService ledger;@Autowired ActivityReviewService review;@Autowired JdbcTemplate jdbc;@Autowired WorkspaceBackupService backups;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));
 @BeforeEach void reset(){var tables=new ArrayList<>(backups.counts().keySet());Collections.reverse(tables);tables.forEach(t->jdbc.update("DELETE FROM "+t));}
 LedgerService.AccountView account(String name,String kind){return ledger.create(new LedgerService.AccountInput(name,kind,today.minusDays(10),"1000.00","Synthetic statement"));}
 LedgerService.AccountView view(String id){return ledger.position(today).accounts().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();}
 LedgerEntry add(String id,String ref,String amount,String kind){ledger.manual(id,new LedgerService.Manual(view(id).revision(),new LedgerService.Row(ref,today,"Synthetic merchant",amount,"BOOKED",kind),"Synthetic source",true));return ledger.activity(id).stream().filter(e->e.reference.equals(ref)).findFirst().orElseThrow();}
 ActivityReviewService.Category category(String name){return review.category(null,new ActivityReviewService.CategoryInput(null,name,null,false,"100.00",null,"Synthetic category decision"));}
 void classify(LedgerEntry e,String c,boolean rule){review.classify(e.id,new ActivityReviewService.Classification(view(e.accountId).revision(),c,"CONFIRMED","Synthetic receipt correction",rule));}
 void link(LedgerEntry e,LedgerEntry other,String type){review.link(e.id,new ActivityReviewService.Link(view(e.accountId).revision(),other.id,view(other.accountId).revision(),type,"Synthetic link evidence"));}
 @Test void lookalikesRemainSeparateAndOnlyApprovedRulesSuggestWithoutConfirming(){
   var a=account("Synthetic cash","CASH");var first=add(a.id(),"one","-10.00","ACTIVITY");add(a.id(),"two","-10.00","ACTIVITY");var c=category("Food");classify(first,c.id(),true);
   assertEquals(2,ledger.activity(a.id()).size());var offered=review.rules().getFirst();
   assertEquals("NONE",review.queue(null,today,today,null,null).getLast().suggestion().confidence());
   review.approveRule(offered.id(),new ActivityReviewService.RuleApproval(offered.revision(),true,"Synthetic approval"));
   var rows=review.queue(null,today,today,"PROVISIONAL",null);assertEquals(1,rows.size());assertEquals(c.id(),rows.getFirst().suggestion().categoryId());assertEquals("PROVISIONAL",rows.getFirst().entry().reviewState);
   assertThrows(IllegalArgumentException.class,()->review.approveRule(offered.id(),new ActivityReviewService.RuleApproval(0L,false,"Stale approval")));
 }
 @Test void transferAndCardSettlementPreserveCashNetAndExcludeActivityTotals(){
   var cash=account("Synthetic cash","CASH");var debt=account("Synthetic card","DEBT");var debit=add(cash.id(),"pay","-50.00","ACTIVITY");var credit=add(debt.id(),"settle","50.00","ACTIVITY");
   link(debit,credit,"CARD_SETTLEMENT");var p=ledger.position(today);assertEquals(0,p.spendingMinor());assertEquals(0,p.incomeMinor());assertEquals(200000,p.netMinor());assertEquals(95000,p.cashMinor());
   assertThrows(IllegalArgumentException.class,()->ledger.correct(cash.id(),debit.id,new LedgerService.Correction(view(cash.id()).revision(),new LedgerService.Row("pay",today,"Changed","-10.00","BOOKED","ACTIVITY"),"Synthetic correction")));
   review.unlink(debit.id,new ActivityReviewService.Link(view(cash.id()).revision(),credit.id,view(debt.id()).revision(),"CARD_SETTLEMENT","Synthetic unlink"));assertEquals(5000,ledger.position(today).spendingMinor());
 }
 @Test void cumulativeRefundsAndReversalsAreBoundedAndReduceSpending(){
   var a=account("Synthetic cash","CASH");var original=add(a.id(),"purchase","-100.00","ACTIVITY");var r1=add(a.id(),"refund1","40.00","ACTIVITY");var r2=add(a.id(),"refund2","60.00","ACTIVITY");var excess=add(a.id(),"excess","0.01","ACTIVITY");
   link(r1,original,"REFUND");link(r2,original,"REVERSAL");assertEquals(0,ledger.position(today).spendingMinor());assertEquals(1,ledger.position(today).incomeMinor());
   assertThrows(IllegalArgumentException.class,()->link(excess,original,"REFUND"));
 }
 @Test void protectedDatesBlockAllFactMutationsUntilExplicitAuditedReopen(){
   var a=account("Synthetic cash","CASH");var e=add(a.id(),"purchase","-10.00","ACTIVITY");var c=category("Interest & Fees");classify(e,c.id(),false);
   ledger.reconcile(a.id(),today,new LedgerService.Reconcile(view(a.id()).revision(),"990.00","Synthetic statement",true,false));
   assertThrows(IllegalArgumentException.class,()->classify(e,c.id(),false));assertThrows(IllegalArgumentException.class,()->add(a.id(),"late","-1.00","ACTIVITY"));
   assertThrows(IllegalArgumentException.class,()->ledger.reconcile(a.id(),today.minusDays(1),new LedgerService.Reconcile(view(a.id()).revision(),"1000.00","Earlier statement",true,false)));
   review.reopen(a.id(),new ActivityReviewService.Reopen(view(a.id()).revision(),"Synthetic protected correction reason"));classify(e,c.id(),false);
   var h=ledger.history(a.id()).stream().filter(x->x.action.equals("REOPEN")).findFirst().orElseThrow();assertEquals("owner",h.actorId);assertTrue(h.beforeJson.contains("throughDate"));assertFalse(view(a.id()).reconciled());
 }
 @Test void safeRenameRetireAndMergeKeepHistoryAndRejectCycles(){
   var a=account("Synthetic cash","CASH");var e=add(a.id(),"purchase","-10.00","ACTIVITY");var food=category("Food");var home=category("Home");classify(e,food.id(),true);
   var renamed=review.category(food.id(),new ActivityReviewService.CategoryInput(food.revision(),"Groceries",null,false,"100.00",null,"Rename evidence"));assertEquals(food.id(),ledger.activity(a.id()).getFirst().categoryId);
   review.category(food.id(),new ActivityReviewService.CategoryInput(renamed.revision(),"Groceries",null,true,"100.00",home.id(),"Explicit merge evidence"));assertEquals(home.id(),ledger.activity(a.id()).getFirst().categoryId);assertFalse(review.rules().getFirst().approved());
   assertThrows(IllegalArgumentException.class,()->review.category(home.id(),new ActivityReviewService.CategoryInput(home.revision(),"Home",home.id(),false,"0.00",null,"Invalid cycle")));
   assertTrue(ledger.history(a.id()).stream().anyMatch(h->h.action.equals("CATEGORY_MERGE")));
 }
 @Test void privateCounterpartsRulesAndAuditNeverLeakToSharedReaders(){
   var a=account("Synthetic shared cash","CASH");var b=account("Synthetic private account","CASH");var left=add(a.id(),"left","-10.00","ACTIVITY");var right=add(b.id(),"right","10.00","ACTIVITY");link(left,right,"TRANSFER");category("Private category");ledger.share(a.id(),new LedgerService.Sharing(view(a.id()).revision(),true));
   var owner=AccessContext.actor();try{AccessContext.enter(new AccessContext.Actor("synthetic-reader","reader",Set.of("READER"),0,false));assertEquals(1,ledger.position(today).accounts().size());assertNull(ledger.activity(a.id()).getFirst().linkedEntryId);assertTrue(ledger.history(a.id()).isEmpty());assertTrue(review.categories().isEmpty());assertTrue(review.queue(null,today,today,null,null).isEmpty());assertThrows(RuntimeException.class,()->review.reopen(a.id(),new ActivityReviewService.Reopen(0L,"Attack")));}finally{AccessContext.enter(owner);}
 }
 @Test void concurrencyAdmitsOnlyOneSameRevisionCorrection()throws Exception{
   var a=account("Synthetic cash","CASH");var e=add(a.id(),"purchase","-10.00","ACTIVITY");var c=category("Food");long revision=view(a.id()).revision();var actor=AccessContext.actor();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
   try{List<Future<Boolean>> results=new ArrayList<>();for(int i=0;i<2;i++)results.add(pool.submit(()->{AccessContext.enter(actor);try{start.await();review.classify(e.id,new ActivityReviewService.Classification(revision,c.id(),"CONFIRMED","Concurrent review",false));return true;}catch(IllegalArgumentException ex){return false;}finally{AccessContext.clear();}}));start.countDown();int admitted=0;for(var f:results)if(f.get(30,TimeUnit.SECONDS))admitted++;assertEquals(1,admitted);}finally{pool.shutdownNow();}
 }
 @Test void reviewNeverAutoClosesUnresolvedIssuesAndRetainsEvidenceAfterLaterChanges(){
   var a=account("Synthetic cash","CASH");var e=add(a.id(),"purchase","-10.00","ACTIVITY");assertThrows(IllegalArgumentException.class,()->review.complete(new ActivityReviewService.Completion(today,"Review",true,review.visibleAuditCount())));
   classify(e,category("Food").id(),false);ledger.reconcile(a.id(),today,new LedgerService.Reconcile(view(a.id()).revision(),"990.00","Synthetic source statement",true,false));
   long token=review.visibleAuditCount();review.complete(new ActivityReviewService.Completion(today,"Synthetic dated review",true,token));assertEquals(1,review.guide(today).completedReviews().size());assertThrows(IllegalArgumentException.class,()->review.complete(new ActivityReviewService.Completion(today,"Stale review",true,token)));
   review.reopen(a.id(),new ActivityReviewService.Reopen(view(a.id()).revision(),"New evidence"));assertEquals(1,review.guide(today).completedReviews().size());assertTrue(review.guide(today).checks().stream().anyMatch(s->s.contains("resolve any difference")));
 }
 @Test void reviewHttpRequiresSessionCsrfAndStrictTypes()throws Exception{
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ledger/review/categories")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
   var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-review-password\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent()).andReturn().getResponse();
   var cookie=response.getCookie("workspace_session");
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ledger/review/categories").cookie(cookie).contentType("application/json").content("{}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
   for(String bad:List.of("null","{\"unknown\":true}","{\"revision\":0.5}","{\"retired\":\"true\"}","{\"name\":123}"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ledger/review/categories").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content(bad)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
   assertTrue(review.categories().isEmpty());
 }
 @Test void relationshipReviewPreservesIdenticalSourceReplayBeforeAndAfterReconciliation(){
   for(String type:List.of("TRANSFER","CARD_SETTLEMENT","REFUND")){
     var a=account("Synthetic source "+type,"CASH");var b=type.equals("REFUND")?a:account("Synthetic counterpart "+type,type.equals("CARD_SETTLEMENT")?"DEBT":"CASH");
     var original=add(a.id(),"original-"+type,"-10.00","ACTIVITY");var incoming=add(b.id(),"incoming-"+type,"4.00","ACTIVITY");
     if(!type.equals("REFUND")){
       ledger.correct(b.id(),incoming.id,new LedgerService.Correction(view(b.id()).revision(),new LedgerService.Row(incoming.reference,today,"Synthetic merchant","10.00","BOOKED","ACTIVITY"),"Synthetic source correction before linking"));incoming=ledger.activity(b.id()).getFirst();
     }
     if(type.equals("REFUND"))link(incoming,original,type);else link(original,incoming,type);
     replay(a.id(),original.reference,"-10.00");replay(b.id(),incoming.reference,type.equals("REFUND")?"4.00":"10.00");
     for(String id:new HashSet<>(List.of(a.id(),b.id()))){var current=view(id);ledger.reconcile(id,today,new LedgerService.Reconcile(current.revision(),java.math.BigDecimal.valueOf(current.bookedMinor(),2).toPlainString(),"Synthetic reconciled source",true,false));}
     replay(a.id(),original.reference,"-10.00");replay(b.id(),incoming.reference,type.equals("REFUND")?"4.00":"10.00");
     var changed=ledger.preview(a.id(),new LedgerService.PreviewInput("reference,date,description,amount,status,kind\n"+original.reference+","+today+",Synthetic merchant,-11.00,BOOKED,ACTIVITY\n","Synthetic changed source"));assertEquals("CONFLICT",changed.proposals().getFirst().disposition());
     var statusChanged=ledger.preview(a.id(),new LedgerService.PreviewInput("reference,date,description,amount,status,kind\n"+original.reference+","+today+",Synthetic merchant,-10.00,PENDING,ACTIVITY\n","Synthetic changed booking state"));assertEquals("CONFLICT",statusChanged.proposals().getFirst().disposition());
   }
 }
 void replay(String id,String reference,String amount){
   var before=ledger.activity(id);long revision=view(id).revision(),balance=view(id).bookedMinor();var preview=ledger.preview(id,new LedgerService.PreviewInput("reference,date,description,amount,status,kind\n"+reference+","+today+",Synthetic merchant,"+amount+",BOOKED,ACTIVITY\n","Synthetic identical replay"));assertEquals("DUPLICATE",preview.proposals().getFirst().disposition());
   var result=ledger.approve(id,preview.id(),new LedgerService.Approval(revision,List.of(preview.proposals().getFirst().row()),true));assertEquals(0,result.added());assertEquals(1,result.duplicates());assertEquals(revision,view(id).revision());assertEquals(balance,view(id).bookedMinor());assertEquals(before.size(),ledger.activity(id).size());
   for(var e:before){var after=ledger.activity(id).stream().filter(x->x.id.equals(e.id)).findFirst().orElseThrow();assertEquals(e.linkedEntryId,after.linkedEntryId);assertEquals(e.linkType,after.linkType);assertEquals(e.reviewState,after.reviewState);}
 }
 @Test void frozenSyntheticBaselineMatchesProductionReviewQueue() throws Exception {
   var json=new com.fasterxml.jackson.databind.ObjectMapper();
   var corpus=json.readTree(java.nio.file.Path.of("../AnalysisScripts/classification/fixtures.json").toFile());
   var a=account("Synthetic baseline","CASH");
   var ids=new HashMap<String,String>();
   for(var c:corpus.path("categories")) ids.put(c.path("id").asText(),category(c.path("name").asText()).id());
   int index=0;
   for(var rule:corpus.path("approvedRules")){
     long r=view(a.id()).revision();String ref="seed-"+index++;
     ledger.manual(a.id(),new LedgerService.Manual(r,new LedgerService.Row(ref,today,rule.get(0).asText(),"-1.00","BOOKED","ACTIVITY"),"Fixture",true));
     var e=ledger.activity(a.id()).stream().filter(x->x.reference.equals(ref)).findFirst().orElseThrow();
     classify(e,ids.get(rule.get(1).asText()),true);
     var offered=review.rules().stream().filter(x->!x.approved()).findFirst().orElseThrow();
     review.approveRule(offered.id(),new ActivityReviewService.RuleApproval(offered.revision(),true,"Fixture rule"));
   }
   int recommended=0;
   for(var c:corpus.path("cases")){
     String ref="case-"+index++;
     ledger.manual(a.id(),new LedgerService.Manual(view(a.id()).revision(),new LedgerService.Row(ref,today,c.get(0).asText(),"-1.00","BOOKED","ACTIVITY"),"Fixture",true));
     var item=review.queue(a.id(),today,today,null,null).stream().filter(x->x.entry().reference.equals(ref)).findFirst().orElseThrow();
     if(item.suggestion().categoryId()!=null){
       assertEquals(ids.get(c.get(2).get(0).asText()),item.suggestion().categoryId());recommended++;
     }
   }
   assertEquals(1,recommended);
 }
 @Test void merchantAcceptanceChecksOwnerRevisionAndPreservesSourceFacts(){
   var a=account("Synthetic label account","CASH");var e=add(a.id(),"label","-10.00","ACTIVITY");
   long revision=view(a.id()).revision();
   var input=new ActivityReviewService.Merchant(revision,"Reviewed shop");
   var owner=AccessContext.actor();
   try {
     AccessContext.enter(new AccessContext.Actor("other-owner","other",Set.of("EDITOR"),0,false));
     assertThrows(RuntimeException.class,()->review.suggestionEntry(e.id,revision));
     assertThrows(RuntimeException.class,()->review.merchant(e.id,input));
   } finally {AccessContext.enter(owner);}
   review.merchant(e.id,input);
   assertThrows(IllegalArgumentException.class,()->review.merchant(e.id,input));
   var saved=ledger.activity(a.id()).getFirst();
   assertEquals(e.description,saved.description);assertEquals(e.reference,saved.reference);
   assertEquals(e.amountMinor,saved.amountMinor);assertEquals("Reviewed shop",saved.reviewedMerchant);
   replay(a.id(),"label","-10.00");
 }
 @Test void adjustmentCannotEnterEitherSideOfTransferOrLoseItsSourcedIdentity(){
   var a=account("Synthetic ordinary source","CASH");var b=account("Synthetic adjustment source","CASH");var debit=add(a.id(),"debit","-10.00","ACTIVITY");
   ledger.reconcile(b.id(),today,new LedgerService.Reconcile(0L,"1010.00","Synthetic sourced adjustment",true,true));var adjustment=ledger.activity(b.id()).getFirst();review.reopen(b.id(),new ActivityReviewService.Reopen(view(b.id()).revision(),"Synthetic explicit reopening"));
   long ar=view(a.id()).revision(),br=view(b.id()).revision(),auditCount=review.visibleAuditCount();
   assertThrows(IllegalArgumentException.class,()->link(debit,adjustment,"TRANSFER"));assertThrows(IllegalArgumentException.class,()->link(adjustment,debit,"TRANSFER"));
   var kept=ledger.activity(b.id()).getFirst();assertEquals("ADJUSTMENT",kept.kind);assertNull(kept.linkedEntryId);assertEquals(1000,kept.amountMinor);assertEquals(ar,view(a.id()).revision());assertEquals(br,view(b.id()).revision());assertEquals(auditCount,review.visibleAuditCount());
 }
 @Test void unlinkKeepsSourceTransferFactsAndRestoresTheMissingCounterpartIssue(){
   var a=account("Synthetic first cash","CASH");var b=account("Synthetic second cash","CASH");var left=add(a.id(),"left","-10.00","TRANSFER");var right=add(b.id(),"right","10.00","TRANSFER");link(left,right,"TRANSFER");
   review.unlink(left.id,new ActivityReviewService.Link(view(a.id()).revision(),right.id,view(b.id()).revision(),"TRANSFER","Synthetic reviewed unlink"));assertEquals("TRANSFER",ledger.activity(a.id()).getFirst().kind);assertEquals(2,review.queue(null,today,today,"COUNTERPART",null).size());assertEquals(0,ledger.position(today).spendingMinor());
 }
}
