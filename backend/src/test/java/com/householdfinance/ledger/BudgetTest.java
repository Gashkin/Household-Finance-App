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

@SpringBootTest(properties={"workspace.password=synthetic-budget-password","spring.datasource.url=jdbc:h2:mem:budget;DB_CLOSE_DELAY=-1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class BudgetTest {
 @Autowired BudgetService budgets;@Autowired ActivityReviewService review;@Autowired LedgerService ledger;@Autowired WorkspaceBackupService backups;@Autowired JdbcTemplate jdbc;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 String food,home;LocalDate day=LocalDate.now(ZoneId.of("Europe/Dublin"));
 @BeforeEach void reset(){var tables=new ArrayList<>(backups.counts().keySet());Collections.reverse(tables);tables.forEach(t->jdbc.update("DELETE FROM "+t));food=category("Food");home=category("Home");}
 String category(String name){return review.category(null,new ActivityReviewService.CategoryInput(null,name,null,false,"0.00",null,"Synthetic category")).id();}
 BudgetService.LineInput line(String id,String amount,boolean carry){return new BudgetService.LineInput(id,amount,carry,null);}
 BudgetService.Template template(String grouping,int pay){return budgets.template(null,new BudgetService.TemplateInput(null,"Synthetic regular plan","100.00",grouping,pay,List.of(line(food,"60.00",true),line(home,"40.00",false)),"Synthetic allocation decision"));}
 BudgetService.Period period(BudgetService.Template t,LocalDate anchor){return budgets.instantiate(t.id(),new BudgetService.Instantiate(t.revision(),anchor,"Synthetic period decision"));}
 BudgetService.Change change(BudgetService.Period p,String income,String a,String b){return new BudgetService.Change(p.revision(),income,List.of(line(food,a,true),line(home,b,false)),"Synthetic deliberate reallocation");}
 String account(){return ledger.create(new LedgerService.AccountInput("Synthetic cash","CASH",day.minusYears(2),"1000.00","Synthetic statement")).id();}
 long revision(String a){return ledger.position(day).accounts().stream().filter(x->x.id().equals(a)).findFirst().orElseThrow().revision();}
 LedgerEntry entry(String a,String ref,LocalDate date,String amount,String category,boolean confirm){ledger.manual(a,new LedgerService.Manual(revision(a),new LedgerService.Row(ref,date,"Synthetic activity",amount,"BOOKED","ACTIVITY"),"Synthetic source",true));var e=ledger.activity(a).stream().filter(x->x.reference.equals(ref)).findFirst().orElseThrow();if(category!=null)review.classify(e.id,new ActivityReviewService.Classification(revision(a),category,confirm?"CONFIRMED":"PROVISIONAL","Synthetic classification",false));return e;}
 @Test void exactMoneyInvalidInputAndRejectedChangesPreserveOriginalAuditAndBalances(){
   var t=template("CALENDAR",1);var p=period(t,day);String a=account();long balance=ledger.position(day).netMinor();var before=budgets.report(p.id());int history=budgets.history(p.id()).size();
   for(String bad:List.of("1.001","1e2","-1.00","NaN","10000000001.00"))assertThrows(IllegalArgumentException.class,()->budgets.change(p.id(),change(p,bad,"0.00","0.00")));
   assertThrows(IllegalArgumentException.class,()->budgets.change(p.id(),change(p,"10.00","10.00","0.01")));
   assertThrows(IllegalArgumentException.class,()->budgets.change(p.id(),new BudgetService.Change(0L,"100.00",List.of(line(food,"1.00",true),line(food,"2.00",false)),"Duplicate")));
   assertThrows(IllegalArgumentException.class,()->budgets.change(p.id(),new BudgetService.Change(0L,"100.00",List.of(new BudgetService.LineInput(food,"1.00",true,p.end())),"Invalid allocation date")));
   assertEquals(before,budgets.report(p.id()));assertEquals(history,budgets.history(p.id()).size());assertEquals(balance,ledger.position(day).netMinor());assertTrue(ledger.activity(a).isEmpty());
   var next=budgets.change(p.id(),change(p,"80.00","50.01","29.99"));assertEquals(0,budgets.report(p.id()).unallocatedMinor());assertEquals(p.original(),next.original());assertEquals(10000,next.originalIncomeMinor());assertEquals(8000,next.incomeMinor());
   assertThrows(IllegalArgumentException.class,()->budgets.change(p.id(),change(p,"100.00","60.00","40.00")));assertEquals(2,budgets.history(p.id()).size());assertEquals("owner",budgets.history(p.id()).getLast().actorId);
 }
 @Test void payDayBoundariesLeapYearsAndCalendarOverlapAreDeterministic(){
   var t=template("PAY_DAY",31);var feb=period(t,LocalDate.of(2024,2,29));assertEquals(LocalDate.of(2024,2,29),feb.start());assertEquals(LocalDate.of(2024,3,31),feb.end());
   var jan=period(t,LocalDate.of(2024,2,28));assertEquals(LocalDate.of(2024,1,31),jan.start());assertEquals(feb.start(),jan.end());
   assertThrows(IllegalArgumentException.class,()->period(t,LocalDate.of(2024,3,1)));assertEquals(2,budgets.periods().size());
   var calendar=template("CALENDAR",1);assertThrows(IllegalArgumentException.class,()->period(calendar,LocalDate.of(2024,2,1)));
 }
 @Test void bookedReviewedActualsRefundsTransfersLatePayAndAllocationDatesStaySeparate(){
   var t=template("CALENDAR",1);var p=period(t,day);String a=account();var purchase=entry(a,"purchase",day,"-10.01",food,true);entry(a,"provisional",day,"-2.00",food,false);entry(a,"uncategorised",day,"-1.00",null,false);
   var refund=entry(a,"refund",day,"4.00",null,false);review.link(refund.id,new ActivityReviewService.Link(revision(a),purchase.id,revision(a),"REFUND","Synthetic refund"));
   var transfer=entry(a,"transfer",day,"-3.00",food,true);String b=account();var counterpart=entry(b,"counterpart",day,"3.00",null,false);review.link(transfer.id,new ActivityReviewService.Link(revision(a),counterpart.id,revision(b),"TRANSFER","Synthetic internal transfer"));
   var report=budgets.report(p.id());assertEquals(601,report.actualMinor());assertEquals(0,report.confirmedIncomeMinor());assertEquals(2,report.unresolved());assertTrue(report.warnings().stream().anyMatch(x->x.contains("late")));
   var changed=budgets.change(p.id(),new BudgetService.Change(0L,"100.00",List.of(new BudgetService.LineInput(food,"60.00",true,p.start()),line(home,"40.00",false)),"Synthetic allocation date"));assertEquals(601,budgets.report(p.id()).actualMinor());
   if(day.isAfter(p.start())){entry(a,"salary",day.minusDays(1),"101.00",food,true);assertEquals(10100,budgets.report(p.id()).confirmedIncomeMinor());}
   assertEquals(p.original(),changed.original());
 }
 @Test void rolloverSnapshotsRetainLateCorrectionsAndResetCategoriesStartFresh(){
   var t=template("CALENDAR",1);var p=period(t,day.minusMonths(1));String a=account();entry(a,"last-month",p.start(),"-10.00",food,true);entry(a,"last-home",p.start(),"-5.00",home,true);
   var next=period(t,day);assertEquals(5000,next.rollover().get(food));assertFalse(next.rollover().containsKey(home));
   entry(a,"late",p.start(),"-1.00",food,true);var report=budgets.report(next.id());assertEquals(5000,report.rows().stream().filter(r->r.categoryId().equals(food)).findFirst().orElseThrow().rolloverMinor());assertTrue(report.warnings().stream().anyMatch(w->w.contains("Earlier period changed")));
 }
 @Test void refundAndLateSalaryUseTheirOwnBookedPeriodWhilePendingIncomeNeverCounts(){
   var t=template("CALENDAR",1);var previous=period(t,day.minusMonths(1));var current=period(t,day);String a=account();var purchase=entry(a,"previous-purchase",previous.start(),"-12.34",food,true);
   var refund=entry(a,"current-refund",current.start(),"2.34",null,false);review.link(refund.id,new ActivityReviewService.Link(revision(a),purchase.id,revision(a),"REFUND","Synthetic cross-period refund"));entry(a,"late-salary",current.start(),"110.00",food,true);
   ledger.manual(a,new LedgerService.Manual(revision(a),new LedgerService.Row("pending-pay",current.start(),"Synthetic pending salary","20.00","PENDING","ACTIVITY"),"Synthetic source",true));var pending=ledger.activity(a).stream().filter(e->e.reference.equals("pending-pay")).findFirst().orElseThrow();review.classify(pending.id,new ActivityReviewService.Classification(revision(a),food,"CONFIRMED","Synthetic pending classification",false));
   assertEquals(1234,budgets.report(previous.id()).actualMinor());assertEquals(0,budgets.report(previous.id()).confirmedIncomeMinor());var report=budgets.report(current.id());assertEquals(-234,report.actualMinor());assertEquals(11000,report.confirmedIncomeMinor());assertEquals(1,report.unresolved());assertTrue(report.warnings().stream().anyMatch(w->w.contains("exceeds plan")));assertEquals(10000,report.period().incomeMinor());
 }
 @Test void templateUpdatesApplyOnlyByExplicitSelectionAndKeepOriginals(){
   var t=template("CALENDAR",1);var a=period(t,day.minusMonths(1));var b=period(t,day);var c=period(t,day.plusMonths(1));
   var updated=budgets.template(t.id(),new BudgetService.TemplateInput(0L,t.name(),"120.00","CALENDAR",1,List.of(line(food,"70.00",true),line(home,"40.00",false)),"Synthetic raise"));assertEquals(10000,budgets.report(b.id()).period().incomeMinor());
   budgets.apply(b.id(),new BudgetService.Apply(0L,updated.revision(),"FORWARD","Explicit forward application",Map.of(b.id(),0L,c.id(),0L)));assertEquals(10000,budgets.report(a.id()).period().incomeMinor());assertEquals(12000,budgets.report(b.id()).period().incomeMinor());assertEquals(12000,budgets.report(c.id()).period().incomeMinor());assertEquals(10000,budgets.report(c.id()).period().originalIncomeMinor());assertEquals(1000,budgets.report(c.id()).unallocatedMinor());
   assertThrows(IllegalArgumentException.class,()->budgets.apply(b.id(),new BudgetService.Apply(0L,updated.revision(),"SELECTED","Stale",null)));assertEquals(2,budgets.history(c.id()).size());
 }
 @Test void privatePlansAndEvidenceAreNotVisibleToOtherEditorsReadersOrAdmins(){
   var t=template("CALENDAR",1);var p=period(t,day);var owner=AccessContext.actor();try{for(Set<String> roles:List.of(Set.of("EDITOR"),Set.of("READER"),Set.of("ADMIN"))){AccessContext.enter(new AccessContext.Actor("synthetic-other","other",roles,0,false));if(!roles.contains("ADMIN")){assertTrue(budgets.templates().isEmpty());assertTrue(budgets.periods().isEmpty());}assertThrows(RuntimeException.class,()->budgets.report(p.id()));assertThrows(RuntimeException.class,()->budgets.history(p.id()));assertThrows(RuntimeException.class,()->budgets.change(p.id(),change(p,"100.00","60.00","40.00")));}}finally{AccessContext.enter(owner);}assertEquals(1,budgets.history(p.id()).size());
 }
 @Test void retiringATemplateCategoryRejectsNewPeriodsAndBatchApplicationWithoutPartialWrites(){
   var t=template("CALENDAR",1);var p=period(t,day);var next=period(t,day.plusMonths(1));var category=review.categories().stream().filter(c->c.id().equals(food)).findFirst().orElseThrow();review.category(food,new ActivityReviewService.CategoryInput(category.revision(),category.name(),null,true,"0.00",null,"Synthetic retirement"));
   assertThrows(IllegalArgumentException.class,()->budgets.apply(p.id(),new BudgetService.Apply(0L,0L,"FORWARD","Synthetic rejected application",Map.of(p.id(),0L,next.id(),0L))));assertThrows(IllegalArgumentException.class,()->period(t,day.plusMonths(2)));
   assertEquals(0,budgets.report(p.id()).period().revision());assertEquals(0,budgets.report(next.id()).period().revision());assertEquals(1,budgets.history(p.id()).size());assertEquals(1,budgets.history(next.id()).size());assertEquals(2,budgets.periods().size());
 }
 @Test void forwardApplicationCannotOverwriteAChangedOrNewFuturePeriod(){
   var t=template("CALENDAR",1);var p=period(t,day);var future=period(t,day.plusMonths(1));var expected=Map.of(p.id(),0L,future.id(),0L);budgets.change(future.id(),change(future,"100.00","50.00","50.00"));
   assertThrows(IllegalArgumentException.class,()->budgets.apply(p.id(),new BudgetService.Apply(0L,0L,"FORWARD","Synthetic stale forward application",expected)));assertEquals(0,budgets.report(p.id()).period().revision());assertEquals(1,budgets.history(p.id()).size());assertEquals(5000,budgets.report(future.id()).period().lines().getFirst().amountMinor());
   var fresh=Map.of(p.id(),0L,future.id(),1L);period(t,day.plusMonths(2));assertThrows(IllegalArgumentException.class,()->budgets.apply(p.id(),new BudgetService.Apply(0L,0L,"FORWARD","Synthetic newly added period",fresh)));assertEquals(0,budgets.report(p.id()).period().revision());assertEquals(1,budgets.report(future.id()).period().revision());
 }
 @Test void sameRevisionConcurrentChangesAdmitOnlyOneAndLeaveOneAudit()throws Exception{
   var p=period(template("CALENDAR",1),day);var actor=AccessContext.actor();var latch=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var futures=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{AccessContext.enter(actor);try{latch.await();budgets.change(p.id(),change(p,"100.00","59.99","40.01"));return true;}catch(IllegalArgumentException e){return false;}finally{AccessContext.clear();}}));latch.countDown();int wins=0;for(var f:futures)if(f.get(30,TimeUnit.SECONDS))wins++;assertEquals(1,wins);assertEquals(2,budgets.history(p.id()).size());assertEquals(1,budgets.report(p.id()).period().revision());}finally{pool.shutdownNow();}
 }
 @Test void httpRequiresSessionCsrfAndStrictMoneyAndRevisionTypes()throws Exception{
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ledger/budgets/templates")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
   var r=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-budget-password\"}")).andReturn().getResponse();var cookie=r.getCookie("workspace_session");
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ledger/budgets/templates").cookie(cookie).contentType("application/json").content("{}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
   for(String bad:List.of("null","{\"unknown\":true}","{\"revision\":0.1}","{\"incomeEuro\":100}","{\"payDay\":\"31\"}"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ledger/budgets/templates").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content(bad)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());assertTrue(budgets.templates().isEmpty());
 }
}
