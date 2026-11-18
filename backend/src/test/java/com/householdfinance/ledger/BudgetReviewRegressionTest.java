package com.householdfinance.ledger;

import com.householdfinance.access.*;
import com.householdfinance.workspace.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"workspace.password=synthetic-budget-review-password","spring.datasource.url=jdbc:h2:mem:budget-review;DB_CLOSE_DELAY=-1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class BudgetReviewRegressionTest {
 @Autowired BudgetService budgets;@Autowired ActivityReviewService review;@Autowired LedgerService ledger;@Autowired WorkspaceBackupService backups;@Autowired JdbcTemplate jdbc;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 LocalDate day=LocalDate.now(ZoneId.of("Europe/Dublin"));String category,account;
 @BeforeEach void reset(){var tables=new ArrayList<>(backups.counts().keySet());Collections.reverse(tables);tables.forEach(t->jdbc.update("DELETE FROM "+t));category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic review category",null,false,"0.00",null,"Synthetic category")).id();account=ledger.create(new LedgerService.AccountInput("Synthetic review cash","CASH",day.minusYears(2),"1000.00","Synthetic statement")).id();}
 long revision(){return ledger.position(day).accounts().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow().revision();}
 LedgerEntry entry(String reference,LocalDate date,String amount){ledger.manual(account,new LedgerService.Manual(revision(),new LedgerService.Row(reference,date,"Synthetic reviewed activity",amount,"BOOKED","ACTIVITY"),"Synthetic source",true));var e=ledger.activity(account).stream().filter(x->x.reference.equals(reference)).findFirst().orElseThrow();review.classify(e.id,new ActivityReviewService.Classification(revision(),category,"CONFIRMED","Synthetic classification",false));return e;}
 BudgetService.Template template(){return budgets.template(null,new BudgetService.TemplateInput(null,"Synthetic review plan","100.00","CALENDAR",1,List.of(new BudgetService.LineInput(category,"100.00",true,null)),"Synthetic allocation"));}
 BudgetService.Period period(BudgetService.Template t,LocalDate date){return budgets.instantiate(t.id(),new BudgetService.Instantiate(t.revision(),date,"Synthetic period"));}
 @ParameterizedTest @CsvSource({"false,30.00","false,100.00","true,30.00","true,100.00"})
 void signedIncomeReversalsWithinAndAcrossPeriods(boolean crossPeriod,String reversed){
   var t=template();var first=period(t,day.minusMonths(1));var second=period(t,day);var salary=entry("salary",first.start(),"100.00");var reversal=entry("salary-reversal",crossPeriod?second.start():first.start().plusDays(1),"-"+reversed);review.link(reversal.id,new ActivityReviewService.Link(revision(),salary.id,revision(),"REVERSAL","Synthetic salary reversal"));
   long amount=new java.math.BigDecimal(reversed).movePointRight(2).longValueExact(),balance=ledger.position(day).netMinor(),accountRevision=revision();int bankHistory=ledger.history(account).size();
   var source=budgets.report(first.id());var target=budgets.report(crossPeriod?second.id():first.id());assertEquals(crossPeriod?10000:10000-amount,source.confirmedIncomeMinor());assertEquals(crossPeriod?-amount:10000-amount,target.confirmedIncomeMinor());assertEquals(0,target.actualMinor());assertTrue(target.warnings().stream().anyMatch(w->w.contains("below planned income")));assertEquals(110000-amount,balance);assertEquals(balance,ledger.position(day).netMinor());assertEquals(accountRevision,revision());assertEquals(bankHistory,ledger.history(account).size());
 }
 @Test void explicitRolloverCorrectionPreservesOriginalSnapshotAndBankFacts()throws Exception{
   var t=template();var previous=period(t,day.minusMonths(1));var current=period(t,day);current=budgets.change(current.id(),new BudgetService.Change(0L,"0.00",List.of(new BudgetService.LineInput(category,"0.00",true,null)),"Synthetic zero new allocation"));assertEquals(10000,budgets.report(current.id()).rows().getFirst().remainingMinor());entry("late-expense",previous.start(),"-100.00");long balance=ledger.position(day).netMinor(),accountRevision=revision();int bankHistory=ledger.history(account).size();
   var login=mvc.perform(post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-budget-review-password\"}")).andExpect(status().isNoContent()).andReturn().getResponse();
   mvc.perform(post("/api/ledger/budgets/periods/"+current.id()+"/rollover").cookie(login.getCookie("workspace_session")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   for(String bad:List.of("{\"revision\":\"1\"}","{\"amountsMinor\":{\"x\":0.5}}","{\"unknown\":true}"))mvc.perform(post("/api/ledger/budgets/periods/"+current.id()+"/rollover").cookie(login.getCookie("workspace_session")).header("X-Workspace-Request","1").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
   mvc.perform(post("/api/ledger/budgets/periods/"+current.id()+"/rollover").cookie(login.getCookie("workspace_session")).header("X-Workspace-Request","1").contentType("application/json").content("{\"revision\":1,\"predecessorRevision\":0,\"amountsMinor\":{\""+category+"\":0},\"reason\":\"Synthetic sourced late correction\"}")).andExpect(status().isOk());
   var result=budgets.report(current.id());assertEquals(10000,result.period().rollover().get(category));assertEquals(0,result.rows().getFirst().rolloverMinor());assertEquals(0,result.rows().getFirst().remainingMinor());assertEquals(current.original(),result.period().original());assertEquals(3,budgets.history(current.id()).size());assertEquals("BUDGET_ROLLOVER",budgets.history(current.id()).getLast().action);assertTrue(budgets.history(current.id()).getLast().beforeJson.contains("10000"));assertEquals(balance,ledger.position(day).netMinor());assertEquals(accountRevision,revision());assertEquals(bankHistory,ledger.history(account).size());assertFalse(result.warnings().stream().anyMatch(w->w.contains("Earlier period changed")));
 }
 BudgetService.Period carryCase(){var t=template();var previous=period(t,day.minusMonths(1));var current=period(t,day);current=budgets.change(current.id(),new BudgetService.Change(0L,"0.00",List.of(new BudgetService.LineInput(category,"0.00",true,null)),"Synthetic zero new allocation"));entry("late-expense",previous.start(),"-100.00");return current;}
 BudgetService.CarryCorrection correction(BudgetService.Period p){var preview=budgets.report(p.id()).carryPreview();return new BudgetService.CarryCorrection(p.revision(),preview.predecessorRevision(),preview.amountsMinor(),"Synthetic sourced carry correction");}
 @Test void stalePreviewInvalidReasonUnresolvedActivityAndDeniedAccessLeaveCarryAndAuditUntouched(){
   var p=carryCase();var input=correction(p);var original=budgets.report(p.id()).period();int history=budgets.history(p.id()).size();
   assertThrows(IllegalArgumentException.class,()->budgets.correctRollover(p.id(),new BudgetService.CarryCorrection(p.revision(),0L,Map.of(category,999L),"Synthetic arbitrary carry")));
   assertThrows(IllegalArgumentException.class,()->budgets.correctRollover(p.id(),new BudgetService.CarryCorrection(p.revision(),0L,input.amountsMinor()," ")));
   entry("later-evidence",p.start().minusDays(1),"-1.00");assertThrows(IllegalArgumentException.class,()->budgets.correctRollover(p.id(),input));
   var preview=correction(p);var owner=AccessContext.actor();try{for(var actor:List.of(new AccessContext.Actor("owner","owner",Set.of("READER"),0,true),new AccessContext.Actor("synthetic-other","other",Set.of("EDITOR"),0,false))){AccessContext.enter(actor);assertThrows(RuntimeException.class,()->budgets.correctRollover(p.id(),preview));}}finally{AccessContext.enter(owner);}
   ledger.manual(account,new LedgerService.Manual(revision(),new LedgerService.Row("unreviewed",p.start().minusDays(1),"Synthetic unreviewed activity","-1.00","BOOKED","ACTIVITY"),"Synthetic receipt",true));assertThrows(IllegalArgumentException.class,()->budgets.correctRollover(p.id(),correction(p)));
   assertEquals(original,budgets.report(p.id()).period());assertEquals(history,budgets.history(p.id()).size());
 }
 @Test void correctionSupportsDeficitsAndSurvivesReallocationTemplateApplicationAndNextRollover(){
   var p=carryCase();entry("extra-late-expense",p.start().minusDays(1),"-1.00");var corrected=budgets.correctRollover(p.id(),correction(p));assertEquals(-100,budgets.report(p.id()).rows().getFirst().remainingMinor());assertEquals(10000,corrected.rollover().get(category));
   assertThrows(IllegalArgumentException.class,()->budgets.correctRollover(p.id(),correction(p)));
   var changed=budgets.change(p.id(),new BudgetService.Change(corrected.revision(),"0.00",List.of(new BudgetService.LineInput(category,"0.00",true,null)),"Synthetic allocation after correction"));
   var applied=budgets.apply(p.id(),new BudgetService.Apply(changed.revision(),0L,"SELECTED","Synthetic explicit template application",null)).getFirst();assertEquals(-100,budgets.report(p.id()).rows().getFirst().rolloverMinor());assertEquals(corrected.correctedRollover(),applied.correctedRollover());
   var t=budgets.templates().getFirst();var next=period(t,day.plusMonths(1));assertEquals(9900,next.rollover().get(category));
 }
 @Test void concurrentCorrectionsAdmitOnlyOneSameRevisionRequest()throws Exception{
   var p=carryCase();var input=correction(p);var actor=AccessContext.actor();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var results=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)results.add(pool.submit(()->{AccessContext.enter(actor);try{start.await();budgets.correctRollover(p.id(),input);return true;}catch(IllegalArgumentException e){return false;}finally{AccessContext.clear();}}));start.countDown();int admitted=0;for(var result:results)if(result.get(30,TimeUnit.SECONDS))admitted++;assertEquals(1,admitted);assertEquals(2,budgets.report(p.id()).period().revision());assertEquals(3,budgets.history(p.id()).size());assertEquals(0,budgets.report(p.id()).rows().getFirst().rolloverMinor());assertEquals(10000,budgets.report(p.id()).period().rollover().get(category));}finally{pool.shutdownNow();}
 }
 @Test void explicitCorrectionHonoursAPredecessorResetWithoutLosingTheOriginalCarry(){
   var t=template();var previous=period(t,day.minusMonths(1));var current=period(t,day);budgets.change(previous.id(),new BudgetService.Change(0L,"100.00",List.of(new BudgetService.LineInput(category,"100.00",false,null)),"Synthetic explicit category reset"));
   assertEquals(10000,budgets.report(current.id()).rows().getFirst().rolloverMinor());assertEquals(0,budgets.report(current.id()).carryPreview().amountsMinor().get(category));var corrected=budgets.correctRollover(current.id(),correction(current));assertEquals(10000,corrected.rollover().get(category));assertEquals(0,budgets.report(current.id()).rows().getFirst().rolloverMinor());assertEquals(1L,corrected.correctedRollover().predecessorRevision());assertEquals(2,budgets.history(current.id()).size());
 }
}
