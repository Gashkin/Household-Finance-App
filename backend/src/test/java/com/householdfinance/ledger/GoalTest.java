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

@SpringBootTest(properties={"workspace.password=synthetic-goal-password","spring.datasource.url=jdbc:h2:mem:goals;DB_CLOSE_DELAY=-1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class GoalTest {
 @Autowired GoalService goals;@Autowired LedgerService ledger;@Autowired CommitmentService commitments;@Autowired ActivityReviewService review;@Autowired BudgetService budgets;@Autowired WorkspaceBackupService backups;@Autowired JdbcTemplate jdbc;@Autowired org.springframework.test.web.servlet.MockMvc mvc;
 LocalDate day=LocalDate.now(ZoneId.of("Europe/Dublin"));String account,category;
 @BeforeEach void reset(){var tables=new ArrayList<>(backups.counts().keySet());Collections.reverse(tables);tables.forEach(t->jdbc.update("DELETE FROM "+t));account=ledger.create(new LedgerService.AccountInput("Synthetic savings cash","CASH",day.minusYears(1),"1000.00","Synthetic statement")).id();category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic annual costs",null,false,"0.00",null,"Synthetic category")).id();}
 GoalService.Change change(String id,long revision,String kind,String target,String reserve,String monthly,LocalDate date,boolean cancelled){return new GoalService.Change(revision,UUID.randomUUID().toString(),"Synthetic explicit goal decision",id,"Synthetic "+kind,kind,date,2,target,reserve,monthly,account,kind.equals("BILL")?account:null,kind.equals("BILL")?category:null,true,cancelled,commitments.get().state().revision());}
 @Test void partialFundingCompetingGoalsExactTotalsAndBudgetsStaySeparate(){
   var template=budgets.template(null,new BudgetService.TemplateInput(null,"Synthetic income plan","100.00","CALENDAR",1,List.of(new BudgetService.LineInput(category,"80.00",false,null)),"Synthetic budget"));var period=budgets.instantiate(template.id(),new BudgetService.Instantiate(0L,day,"Synthetic period"));var budget=budgets.report(period.id());
   String holiday=UUID.randomUUID().toString(),buffer=UUID.randomUUID().toString();goals.change(change(holiday,0,"SAVINGS","100.01","30.01","10.00",day.plusMonths(2),false));goals.change(change(buffer,1,"SAVINGS","900.00","900.00","20.00",day.plusMonths(6),false));
   var w=goals.get();assertEquals(93001,w.reservedMinor());assertEquals(6999,w.unreservedMinor());assertEquals(100000,w.cashMinor());assertEquals(3000,w.monthlyMinor());assertEquals(2000,w.budgetUnallocatedMinor());assertEquals(2334,w.rows().stream().filter(r->r.goal().id().equals(holiday)).findFirst().orElseThrow().recommendedMonthlyMinor());assertTrue(w.warnings().stream().anyMatch(n->n.contains("Competing")));assertEquals(budget,budgets.report(period.id()));assertTrue(ledger.activity(account).isEmpty());assertTrue(commitments.get().state().schedules().isEmpty());
   goals.change(change(buffer,2,"SAVINGS","900.00","900.00","20.00",day.plusMonths(6),true));assertEquals(3001,goals.get().reservedMinor());assertEquals(1000,goals.get().monthlyMinor());assertEquals(3,goals.history().size());
 }
 @Test void failedReservationRollsBackLinkedBillCommitmentAndBothAudits(){
   var saved=goals.get().state();var bills=commitments.get().state();long count=jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_AUDIT",Long.class);
   assertThrows(IllegalArgumentException.class,()->goals.change(change(UUID.randomUUID().toString(),0,"BILL","1000.01","1000.01","10.00",day.plusDays(30),false)));
   assertEquals(saved,goals.get().state());assertEquals(bills,commitments.get().state());assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_AUDIT",Long.class));assertEquals(100000,ledger.position(day).cashMinor());assertTrue(ledger.activity(account).isEmpty());
 }
 @Test void annualBillMovedCancelledAndMatchedActualAffectsForecastOnlyOnce(){
   String id=UUID.randomUUID().toString();goals.change(change(id,0,"BILL","120.01","20.01","10.00",day.plusDays(30),false));assertEquals(87999,commitments.outlook(day,day.plusMonths(6),false,true).household().closingMinor());
   goals.change(change(id,1,"BILL","150.01","20.01","10.00",day.plusDays(40),false));var occurrences=commitments.upcoming(day,day.plusMonths(6));assertEquals(1,occurrences.size());assertEquals(day.plusDays(40),occurrences.getFirst().date());assertEquals(84999,commitments.outlook(day,day.plusMonths(6),false,true).household().closingMinor());
   goals.change(change(id,2,"BILL","150.01","20.01","10.00",day.plusDays(40),true));assertTrue(commitments.upcoming(day,day.plusMonths(6)).isEmpty());assertEquals(100000,commitments.outlook(day,day.plusMonths(6),false,true).household().closingMinor());assertEquals(0,goals.get().reservedMinor());
   String other=UUID.randomUUID().toString();goals.change(change(other,3,"BILL","110.00","0.00","10.00",day,false));
   ledger.manual(account,new LedgerService.Manual(0L,new LedgerService.Row("synthetic-payment",day,"Synthetic annual payment","-100.00","BOOKED","ACTIVITY"),"Synthetic statement",true));var e=ledger.activity(account).getFirst();review.classify(e.id,new ActivityReviewService.Classification(1L,category,"CONFIRMED","Synthetic approved cost",false));
   var bill=commitments.upcoming(day,day).getFirst();commitments.exception(new CommitmentService.ExceptionInput(commitments.get().state().revision(),UUID.randomUUID().toString(),"Synthetic actual match",bill.id(),null,null,false,e.id,"MATCH",2L));
   assertEquals(10000,goals.get().rows().stream().filter(r->r.goal().id().equals(other)).findFirst().orElseThrow().actualCostMinor());assertEquals(90000,commitments.outlook(day,day.plusMonths(6),false,true).household().closingMinor());
   var state=goals.get().state();var commitment=commitments.get().state();int history=goals.history().size();assertThrows(IllegalArgumentException.class,()->goals.change(change(other,4,"BILL","120.00","0.00","10.00",day,false)));assertEquals(state,goals.get().state());assertEquals(commitment,commitments.get().state());assertEquals(history,goals.history().size());assertEquals(90000,ledger.position(day).cashMinor());
 }
 @Test void replaysStaleRequestsAndInvalidMoneyLeaveFinancialStateUnchanged(){
   String id=UUID.randomUUID().toString();var in=change(id,0,"SAVINGS","200.00","100.00","10.00",day.plusMonths(1),false);var saved=goals.change(in);assertEquals(saved,goals.change(in));assertTrue(goals.operation(in.operationId()).get("completed"));assertEquals(1,goals.history().size());
   assertThrows(IllegalArgumentException.class,()->goals.change(change(id,0,"SAVINGS","200.00","100.00","10.00",day.plusMonths(1),false)));
   for(String bad:List.of("1.001","1e2","-1.00","NaN","10000000001.00"))assertThrows(IllegalArgumentException.class,()->goals.change(change(id,1,"SAVINGS",bad,"0.00","0.00",day,false)));
   assertEquals(saved,goals.get().state());assertEquals(1,goals.history().size());assertEquals(100000,ledger.position(day).cashMinor());
 }
 @Test void sameRevisionConcurrentReservationsAdmitOneAndDoNotOversubscribe()throws Exception{
   var actor=AccessContext.actor();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);var in=List.of(change(UUID.randomUUID().toString(),0,"SAVINGS","800.00","800.00","1.00",day,false),change(UUID.randomUUID().toString(),0,"SAVINGS","800.00","800.00","1.00",day,false));try{var futures=new ArrayList<Future<Boolean>>();for(var c:in)futures.add(pool.submit(()->{AccessContext.enter(actor);try{gate.await();goals.change(c);return true;}catch(IllegalArgumentException e){return false;}finally{AccessContext.clear();}}));gate.countDown();int wins=0;for(var f:futures)if(f.get(30,TimeUnit.SECONDS))wins++;assertEquals(1,wins);assertEquals(80000,goals.get().reservedMinor());assertEquals(1,goals.history().size());}finally{pool.shutdownNow();}
 }
 @Test void linkedAccountCapacityAndBalanceDropRequireDeliberateRelease(){
   String id=UUID.randomUUID().toString();goals.change(change(id,0,"SAVINGS","1000.00","900.00","10.00",day,false));ledger.manual(account,new LedgerService.Manual(0L,new LedgerService.Row("synthetic-pending",day,"Synthetic pending debit","-200.00","PENDING","ACTIVITY"),"Synthetic source",true));assertEquals(-10000,goals.get().unreservedMinor());assertTrue(goals.get().warnings().stream().anyMatch(w->w.contains("no longer backs")));
   assertThrows(IllegalArgumentException.class,()->goals.change(change(id,1,"SAVINGS","1000.00","901.00","10.00",day,false)));goals.change(change(id,1,"SAVINGS","1000.00","700.00","10.00",day,false));assertEquals(10000,goals.get().unreservedMinor());
 }
 @Test void privateGoalsNeverLeakAcrossEditorsReadersOrAdministrators(){
   String id=UUID.randomUUID().toString();goals.change(change(id,0,"SAVINGS","10.00","1.00","1.00",day,false));var owner=AccessContext.actor();var request=change(id,1,"SAVINGS","10.00","1.00","1.00",day,false);
   try{for(var roles:List.of(Set.of("EDITOR"),Set.of("READER"),Set.of("ADMIN"))){AccessContext.enter(new AccessContext.Actor("synthetic-other","other",roles,0,false));if(roles.contains("ADMIN")){assertThrows(RuntimeException.class,()->goals.get());}else{assertTrue(goals.get().state().goals().isEmpty());assertTrue(goals.history().isEmpty());assertFalse(goals.operation(request.operationId()).get("completed"));}assertThrows(RuntimeException.class,()->goals.change(request));}}finally{AccessContext.enter(owner);}assertEquals(1,goals.history().size());
 }
 @Test void httpAuthenticationCsrfAndStrictTypesRejectWithoutWrites()throws Exception{
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ledger/goals")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
   var r=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-goal-password\"}")).andReturn().getResponse();var cookie=r.getCookie("workspace_session");
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ledger/goals").cookie(cookie).contentType("application/json").content("{}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
   for(String bad:List.of("null","{\"unknown\":true}","{\"revision\":0.1}","{\"targetEuro\":100}","{\"priority\":\"2\"}"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ledger/goals").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content(bad)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());assertTrue(goals.get().state().goals().isEmpty());assertTrue(goals.history().isEmpty());
 }
}
