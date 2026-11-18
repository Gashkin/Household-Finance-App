package com.householdfinance.ledger;

import com.householdfinance.access.*;
import com.householdfinance.forecast.*;
import com.householdfinance.workspace.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"workspace.password=synthetic-commitment-password","spring.datasource.url=jdbc:h2:mem:commitments;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class CommitmentTest {
 @Autowired CommitmentService service;@Autowired LedgerService ledger;@Autowired ActivityReviewService review;@Autowired WorkspaceBackupService backups;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired ForecastService forecast;
 LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));String account,category;
 @BeforeEach void reset(){var tables=new ArrayList<>(backups.counts().keySet());Collections.reverse(tables);tables.forEach(t->jdbc.update("DELETE FROM "+t));account=ledger.create(new LedgerService.AccountInput("Synthetic main","CASH",LocalDate.of(2000,1,1),"1000.00","Synthetic statement")).id();category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic utilities",null,false,"0.00",null,"Synthetic category")).id();}
 String id(){return UUID.randomUUID().toString();}
 long rev(){return service.get().state().revision();}
 CommitmentService.Rule rule(String frequency,LocalDate anchor,String amount){return new CommitmentService.Rule("Synthetic bill",account,category,"EXPENSE","EXPECTED","FIXED",amount,frequency,anchor,null,null,0,null);}
 CommitmentService.Edit edit(String id,long revision,LocalDate effective,CommitmentService.Rule rule){return new CommitmentService.Edit(id,revision,effective,false,rule);}
 CommitmentService.State batch(CommitmentService.Edit... edits){return service.batch(new CommitmentService.Batch(rev(),id(),"Synthetic schedule decision",List.of(edits)));}
 CommitmentService.Schedule create(CommitmentService.Rule r){return batch(edit(id(),0,r.anchor(),r)).schedules().getLast();}
 List<CommitmentService.Occurrence> upcoming(LocalDate from,LocalDate to){return service.upcoming(from,to);}
 CommitmentService.ExceptionInput change(CommitmentService.Occurrence o,String action,String entry,Long accountRevision,LocalDate date,String amount){return new CommitmentService.ExceptionInput(rev(),id(),"Synthetic explicit occurrence decision",o.id(),date,amount,false,entry,action,accountRevision);}
 LedgerEntry activity(String ref,String amount,String status){long revision=ledger.position(today).accounts().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow().revision();ledger.manual(account,new LedgerService.Manual(revision,new LedgerService.Row(ref,today,"Synthetic activity",amount,status,"ACTIVITY"),"Synthetic evidence",true));var entry=ledger.activity(account).stream().filter(e->e.reference.equals(ref)).findFirst().orElseThrow();review.classify(entry.id,new ActivityReviewService.Classification(revision+1,category,"CONFIRMED","Synthetic reviewed category",false));return entry;}
 long accountRev(){return ledger.position(today).accounts().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow().revision();}
 @Test void monthEndLeapYearWeeklyFortnightlyAndOneOffHaveStableOccurrenceIds(){
  var jan=LocalDate.of(2024,1,31);var s=create(rule("MONTHLY",jan,"1.01"));var rows=upcoming(jan,jan.plusMonths(2));assertEquals(List.of(jan,LocalDate.of(2024,2,29),LocalDate.of(2024,3,31)),rows.stream().map(CommitmentService.Occurrence::date).toList());assertEquals(rows,upcoming(jan,jan.plusMonths(2)));
  create(rule("WEEKLY",today,"2.00"));create(rule("FORTNIGHTLY",today,"3.00"));create(rule("ONCE",today,"4.00"));var next=upcoming(today,today.plusDays(28));assertEquals(5,next.stream().filter(o->o.amountMinor()==200).count());assertEquals(3,next.stream().filter(o->o.amountMinor()==300).count());assertEquals(1,next.stream().filter(o->o.amountMinor()==400).count());assertNotNull(s.id());
 }
 @Test void effectiveEditsCancellationAndOverridesPreservePastAndStableFutureIdentity(){
  var s=create(rule("MONTHLY",today.minusMonths(2),"10.00"));var past=upcoming(today.minusMonths(2),today.minusDays(1));var future=upcoming(today,today.plusMonths(2)).getLast();
  service.exception(change(future,"OVERRIDE",null,null,future.date(),"11.00"));batch(edit(s.id(),1,today,rule("MONTHLY",today.minusMonths(2),"20.00")));
  assertEquals(past,upcoming(today.minusMonths(2),today.minusDays(1)));var kept=upcoming(today,today.plusMonths(3)).stream().filter(o->o.id().equals(future.id())).findFirst().orElseThrow();assertEquals(1100,kept.amountMinor());
  service.batch(new CommitmentService.Batch(rev(),id(),"Synthetic cancel",List.of(new CommitmentService.Edit(s.id(),2L,today,true,null))));assertEquals(past,upcoming(today.minusMonths(2),today.minusDays(1)));assertTrue(upcoming(today,today.plusMonths(3)).stream().anyMatch(o->o.id().equals(future.id())));
  assertThrows(IllegalArgumentException.class,()->batch(edit(s.id(),3,today.minusDays(1),rule("MONTHLY",today,"5.00"))));assertEquals(3,service.get().state().schedules().getFirst().revision());
 }
 @Test void failedBatchAndCsvLeaveAllRecordsBalancesAndAuditUnchanged(){
  var a=create(rule("MONTHLY",today,"10.00"));var b=create(rule("WEEKLY",today,"2.00"));var before=service.get().state();int count=service.history().size();long balance=ledger.position(today).cashMinor();
  for(String amount:List.of("1.001","1e2","-1","NaN","0"))assertThrows(IllegalArgumentException.class,()->batch(edit(a.id(),a.revision(),today,rule("MONTHLY",today,amount))));
  assertThrows(IllegalArgumentException.class,()->batch(edit(a.id(),a.revision(),today,rule("MONTHLY",today,"12.00")),edit(b.id(),0,today,rule("WEEKLY",today,"3.00"))));
  String csv=service.csv();var valid=service.preview(new CommitmentService.CsvInput(csv));assertTrue(valid.applicable());assertEquals(2,valid.rows().size());
  String bad=csv.replace("\"10.00\"","\"1.001\"").replace("\"2.00\"","\"1e2\"");var preview=service.preview(new CommitmentService.CsvInput(bad));assertFalse(preview.applicable());assertEquals(2,preview.rows().stream().filter(r->!r.errors().isEmpty()).count());
  assertThrows(IllegalArgumentException.class,()->service.apply(new CommitmentService.CsvApply(rev(),id(),"Synthetic rejected import",bad)));assertEquals(before,service.get().state());assertEquals(count,service.history().size());assertEquals(balance,ledger.position(today).cashMinor());
  service.apply(new CommitmentService.CsvApply(rev(),id(),"Synthetic atomic import",csv));assertEquals(before.revision()+1,rev());assertEquals(count+1,service.history().size());
 }
 @Test void sameOperationReplayAndConcurrentSameRevisionSaveExactlyOnce()throws Exception{
  var r=rule("ONCE",today,"1.00");var request=new CommitmentService.Batch(0L,id(),"Synthetic retry",List.of(edit(id(),0,today,r)));var saved=service.batch(request);assertEquals(saved,service.batch(request));assertEquals(1,service.history().size());
  assertThrows(IllegalArgumentException.class,()->service.batch(new CommitmentService.Batch(1L,request.operationId(),"Changed retry",request.edits())));
  var s=saved.schedules().getFirst();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);List<Future<Boolean>> results=new ArrayList<>();for(int n=0;n<2;n++)results.add(pool.submit(()->OwnerFixture.call(()->{start.await();try{service.batch(new CommitmentService.Batch(1L,id(),"Synthetic race",List.of(edit(s.id(),1,today,r))));return true;}catch(IllegalArgumentException e){return false;}})));start.countDown();int successes=0;for(var f:results)if(f.get(20,TimeUnit.SECONDS))successes++;pool.shutdownNow();assertEquals(1,successes);assertEquals(2,service.history().size());assertEquals(2,rev());
 }
 @Test void approvedActualMatchPreventsDoubleCountingAndChangedOrVoidedEvidenceRequiresReview(){
  create(rule("ONCE",today,"10.00"));var actual=activity("paid","-9.50","BOOKED");var o=upcoming(today,today).getFirst();service.exception(change(o,"MATCH",actual.id,accountRev(),null,null));var outlook=service.outlook(today,today.plusMonths(6),false,false);assertEquals(99050,outlook.household().closingMinor());assertEquals("MATCHED",outlook.occurrences().getFirst().state());assertEquals(950,outlook.occurrences().getFirst().actualMinor());
  var other=create(rule("ONCE",today,"10.00"));var second=upcoming(today,today).stream().filter(x->x.scheduleId().equals(other.id())).findFirst().orElseThrow();long before=rev();assertThrows(IllegalArgumentException.class,()->service.exception(change(second,"MATCH",actual.id,accountRev(),null,null)));assertEquals(before,rev());
  ledger.correct(account,actual.id,new LedgerService.Correction(accountRev(),new LedgerService.Row(actual.reference,today,"Synthetic void","-9.50","VOID","ACTIVITY"),"Synthetic void reason"));assertEquals("MATCH_NEEDS_REVIEW",upcoming(today,today).stream().filter(row->row.id().equals(o.id())).findFirst().orElseThrow().state());assertThrows(IllegalArgumentException.class,()->service.outlook(today,today.plusMonths(6),false,false));
 }
 @Test void pendingReservationIsCountedOnceAndSettlementRequiresExplicitMatch(){
  create(rule("ONCE",today,"10.00"));var actual=activity("pending","-10.00","PENDING");var o=upcoming(today,today).getFirst();service.exception(change(o,"RESERVE",actual.id,accountRev(),null,null));assertEquals(99000,service.outlook(today,today.plusMonths(6),false,false).household().closingMinor());
  ledger.correct(account,actual.id,new LedgerService.Correction(accountRev(),new LedgerService.Row(actual.reference,today,"Synthetic settled","-10.00","BOOKED","ACTIVITY"),"Synthetic settlement"));assertEquals("MATCH_NEEDS_REVIEW",upcoming(today,today).getFirst().state());
  review.classify(actual.id,new ActivityReviewService.Classification(accountRev(),category,"CONFIRMED","Synthetic settlement approved",false));service.exception(change(o,"MATCH",actual.id,accountRev(),null,null));assertEquals(99000,service.outlook(today,today.plusMonths(6),false,false).household().closingMinor());
 }
 @Test void payrollDelayUncertainIncomeAndTransferAccountRiskRemainConservative(){
  String payroll=id();var anchor=today.plusDays(1);service.payroll(new CommitmentService.PayrollEdit(0L,id(),"Synthetic calendar",payroll,0L,today,new CommitmentService.PayrollRule("Synthetic salary",anchor.getDayOfMonth(),List.of(6,7),"NONE",List.of(),Map.of(anchor,anchor.plusDays(3)))));
  var income=new CommitmentService.Rule("Synthetic salary",account,category,"INCOME","UNCERTAIN","FIXED","50.00","INCOME_LINKED",anchor,null,payroll,0,null);create(income);var occurrences=upcoming(today,today.plusMonths(6));assertEquals(anchor.plusDays(3),occurrences.getFirst().date());String stable=occurrences.getFirst().id();
  var base=service.outlook(today,today.plusMonths(6),false,false);var upside=service.outlook(today,today.plusMonths(6),true,false);assertEquals(100000,base.household().closingMinor());assertTrue(upside.household().closingMinor()>base.household().closingMinor());assertFalse(upside.currentEvidence());
  String target=ledger.create(new LedgerService.AccountInput("Synthetic spending","CASH",today,"1.20","Synthetic evidence")).id();create(new CommitmentService.Rule("Synthetic transfer",account,category,"TRANSFER","EXPECTED","FIXED","1.00","ONCE",anchor,null,null,0,target));create(new CommitmentService.Rule("Synthetic target bill",target,category,"EXPENSE","EXPECTED","FIXED","2.00","ONCE",anchor.plusDays(1),null,null,0,null));
  var projected=service.outlook(today,today.plusMonths(6),false,false);assertEquals(99920,projected.household().closingMinor());assertEquals(20,projected.accounts().stream().filter(a->a.id().equals(target)).findFirst().orElseThrow().closingMinor());assertTrue(projected.occurrences().stream().anyMatch(o->o.id().equals(stable)));
 }
 @Test void overdueVersusDataUnconfirmedDependsOnCoverageAndMissingCounterpartWarns(){
  create(rule("ONCE",today.minusDays(1),"1.00"));assertEquals("DATA_UNCONFIRMED",upcoming(today.minusDays(1),today).getFirst().state());ledger.reconcile(account,today,new LedgerService.Reconcile(accountRev(),"1000.00","Synthetic reviewed statement",true,false));assertEquals("OVERDUE",upcoming(today.minusDays(1),today).getFirst().state());
  create(new CommitmentService.Rule("Synthetic missing leg",account,category,"TRANSFER","EXPECTED","FIXED","2.00","ONCE",today.plusDays(1),null,null,0,null));var p=service.outlook(today,today.plusMonths(6),false,false);assertTrue(p.warnings().stream().anyMatch(w->w.contains("Missing transfer counterpart")));assertEquals(99700,p.household().closingMinor());
 }
 @Test void privacyDeniedWritesAndHistoryDoNotExposeAnotherOwnersPlan(){
  var s=create(rule("MONTHLY",today,"10.00"));var owner=AccessContext.actor();int count=service.history().size();try{for(var roles:List.of(Set.of("EDITOR"),Set.of("READER"),Set.of("ADMIN"))){AccessContext.enter(new AccessContext.Actor("other","other",roles,0,false));if(!roles.contains("ADMIN")){assertTrue(service.get().state().schedules().isEmpty());assertTrue(service.history().isEmpty());}assertThrows(RuntimeException.class,()->service.batch(new CommitmentService.Batch(0L,id(),"Denied",List.of(edit(s.id(),1,today,rule("ONCE",today,"1.00"))))));}}finally{AccessContext.enter(owner);}assertEquals(count,service.history().size());assertEquals(1,rev());
 }
 @Test void sixCalendarMonthLegacyForecastSurvivesWithoutRecreatingActuals(){
  var input=new ForecastService.Input(0L,"Synthetic saved plan",new ForecastService.BaselineInput("MANUAL",today,"100.00","0.00","Synthetic manual baseline"),today.plusDays(28),"0.00",List.of(new ForecastService.EventInput(id(),today.plusDays(2),"Existing expectation","EXPENSE","EXPECTED","1.00","Synthetic source")),true);
  var old=forecast.save(input).plan();forecast.save(new ForecastService.Input(old.revision(),input.name(),null,today.plusMonths(6),"0.00",input.events(),true));assertEquals(old.inputs().events(),forecast.get().plan().inputs().events());assertTrue(ledger.activity(account).isEmpty());assertTrue(service.outlook(today,today.plusMonths(6),false,false).household().days().size()>=181);
 }
 @Test void unsavedPreviewsAndTransferComparisonChangeNoFinancialRecords(){
  var s=create(rule("MONTHLY",today,"10.00"));var before=service.get().state();var history=service.history().size();long balance=ledger.position(today).cashMinor();
  var preview=service.previewBatch(new CommitmentService.Batch(rev(),id(),"Preview",List.of(edit(s.id(),1,today,rule("MONTHLY",today,"20.00")))));assertEquals(2000,preview.after().getFirst().amountMinor());assertEquals(before,service.get().state());
  String destination=ledger.create(new LedgerService.AccountInput("Synthetic short account","CASH",today,"1.20","Synthetic statement")).id();create(new CommitmentService.Rule("Synthetic shortfall",destination,category,"EXPENSE","EXPECTED","FIXED","2.00","ONCE",today.plusDays(2),null,null,0,null));var saved=service.get().state();int count=service.history().size();
  var comparison=service.compare(new CommitmentService.ComparisonInput(rev(),today,today.plusMonths(6),false,false,account,destination,today.plusDays(1),"1.00",null,null));assertEquals(comparison.base().household().closingMinor(),comparison.scenario().household().closingMinor());assertEquals(comparison.base().household().lowestMinor(),comparison.scenario().household().lowestMinor());assertNotNull(comparison.base().accounts().stream().filter(a->a.id().equals(destination)).findFirst().orElseThrow().shortfallDate());assertNull(comparison.scenario().accounts().stream().filter(a->a.id().equals(destination)).findFirst().orElseThrow().shortfallDate());assertEquals(saved,service.get().state());assertEquals(count,service.history().size());assertEquals(balance+120,ledger.position(today).cashMinor());assertTrue(history>0);
 }
 @Test void payrollPreviewDelayAndLinkedBillsKeepIdentityAndDoNotRewritePaidFacts(){
  String payroll=id();var anchor=today.plusDays(1);var calendar=new CommitmentService.PayrollEdit(rev(),id(),"Synthetic payroll",payroll,0L,today,new CommitmentService.PayrollRule("Synthetic pay",anchor.getDayOfMonth(),List.of(6,7),"NONE",List.of(),Map.of()));var initial=service.get().state();assertEquals(6,service.previewPayroll(calendar).size());assertEquals(initial,service.get().state());service.payroll(calendar);
  create(new CommitmentService.Rule("Synthetic pay",account,category,"INCOME","EXPECTED","FIXED","100.00","INCOME_LINKED",anchor,null,payroll,0,null));create(new CommitmentService.Rule("Synthetic linked bill",account,category,"EXPENSE","EXPECTED","FIXED","20.00","INCOME_LINKED",anchor,null,payroll,2,null));var originals=upcoming(today,today.plusDays(10));var pay=originals.stream().filter(o->o.direction().equals("INCOME")).findFirst().orElseThrow();var bill=originals.stream().filter(o->o.direction().equals("EXPENSE")).findFirst().orElseThrow();
  var comparison=service.compare(new CommitmentService.ComparisonInput(rev(),today,today.plusMonths(6),false,false,null,null,null,null,pay.id(),pay.date().plusDays(3)));assertEquals(bill.date().plusDays(3),comparison.scenario().occurrences().stream().filter(o->o.id().equals(bill.id())).findFirst().orElseThrow().date());assertEquals(originals,upcoming(today,today.plusDays(10)));
  service.exception(change(pay,"OVERRIDE",null,null,pay.date().plusDays(3),null));var moved=upcoming(today,today.plusDays(15));assertEquals(bill.date().plusDays(3),moved.stream().filter(o->o.id().equals(bill.id())).findFirst().orElseThrow().date());assertTrue(moved.stream().anyMatch(o->o.id().equals(pay.id())));
 }
 @Test void oldUnpaidObligationsAreNotSilentlyDroppedAndCsvFormulaTextRoundTrips(){
  create(rule("ONCE",today.minusYears(1),"10.00"));assertEquals(99000,service.outlook(today,today.plusMonths(6),false,false).household().closingMinor());
  for(String name:List.of("=SUM(1,2)","  +formula","'Quoted bill","Line one\nline two")){var r=rule("ONCE",today.plusDays(1),"1.00");create(new CommitmentService.Rule(name,r.accountId(),r.categoryId(),r.direction(),r.certainty(),r.amountType(),r.amountEuro(),r.frequency(),r.anchor(),null,null,0,null));}
  String csv=service.csv();assertTrue(csv.contains("\"'=SUM(1,2)\""));assertTrue(csv.contains("\"'  +formula\""));assertTrue(csv.contains("\"''Quoted bill\""));var preview=service.preview(new CommitmentService.CsvInput(csv));assertTrue(preview.applicable());assertTrue(preview.rows().stream().anyMatch(r->r.edit().rule().name().equals("'Quoted bill")));assertTrue(preview.rows().stream().anyMatch(r->r.edit().rule().name().equals("Line one\nline two")));
 }
 @Test void effectiveDatesUseResolvedPayDatesRatherThanFutureNominalAnchors(){
  var nominal=YearMonth.from(today).atEndOfMonth();var paidDate=today.minusDays(1);String payroll=id();service.payroll(new CommitmentService.PayrollEdit(0L,id(),"Synthetic calendar",payroll,0L,today.minusYears(1),new CommitmentService.PayrollRule("Synthetic late-month anchor",nominal.getDayOfMonth(),List.of(6,7),"NONE",List.of(),Map.of(nominal,paidDate))));
  var r=new CommitmentService.Rule("Synthetic resolved date",account,category,"EXPENSE","EXPECTED","FIXED","10.00","INCOME_LINKED",nominal,null,payroll,0,null);String sid=id();batch(edit(sid,0,today.minusMonths(1),r));var past=upcoming(paidDate,paidDate).getFirst();
  batch(edit(sid,1,today,new CommitmentService.Rule(r.name(),account,category,"EXPENSE","EXPECTED","FIXED","20.00","INCOME_LINKED",nominal,null,payroll,0,null)));assertEquals(past,upcoming(paidDate,paidDate).getFirst());assertEquals(1000,upcoming(paidDate,paidDate).getFirst().amountMinor());
  service.payroll(new CommitmentService.PayrollEdit(rev(),id(),"Synthetic future payroll change",payroll,1L,today,new CommitmentService.PayrollRule("Synthetic new payroll",1,List.of(6,7),"NONE",List.of(),Map.of())));assertEquals(past,upcoming(paidDate,paidDate).getFirst());assertFalse(service.candidates(past.id()).stream().anyMatch(c->c.amountMinor()>0));
 }
 @Test void approvedReciprocalTransferMatchesDoNotRepeatEitherCashLeg(){
  String target=ledger.create(new LedgerService.AccountInput("Synthetic transfer target","CASH",today,"1.00","Synthetic source")).id();var source=activity("transfer-source","-2.00","BOOKED");ledger.manual(target,new LedgerService.Manual(0L,new LedgerService.Row("transfer-target",today,"Synthetic incoming transfer","2.00","BOOKED","ACTIVITY"),"Synthetic counterpart",true));var peer=ledger.activity(target).getFirst();review.classify(peer.id,new ActivityReviewService.Classification(1L,category,"CONFIRMED","Synthetic counterpart approval",false));review.link(source.id,new ActivityReviewService.Link(accountRev(),peer.id,2L,"TRANSFER","Synthetic reciprocal transfer"));
  create(new CommitmentService.Rule("Synthetic planned transfer",account,category,"TRANSFER","EXPECTED","FIXED","2.00","ONCE",today,null,null,0,target));var o=upcoming(today,today).getFirst();assertEquals(1,service.candidates(o.id()).size());service.exception(change(o,"MATCH",source.id,accountRev(),null,null));var out=service.outlook(today,today.plusMonths(6),false,false);assertEquals(100100,out.household().closingMinor());assertEquals(99800,out.accounts().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow().closingMinor());assertEquals(300,out.accounts().stream().filter(a->a.id().equals(target)).findFirst().orElseThrow().closingMinor());assertEquals("MATCHED",out.occurrences().getFirst().state());
 }
 @Test void overdueUnreceivedPayrollCanBeRetimedWithoutInventingReceivedMoney(){
  LocalDate due=today.minusDays(2);String payroll=id();service.payroll(new CommitmentService.PayrollEdit(0L,id(),"Synthetic overdue payroll",payroll,0L,today.minusYears(1),new CommitmentService.PayrollRule("Synthetic overdue salary",due.getDayOfMonth(),List.of(6,7),"NONE",List.of(),Map.of())));
  create(new CommitmentService.Rule("Synthetic unreceived salary",account,category,"INCOME","EXPECTED","FIXED","100.00","INCOME_LINKED",due,null,payroll,0,null));var occurrence=upcoming(due,due).getFirst();long base=service.outlook(today,today.plusMonths(6),false,false).household().closingMinor();long balance=ledger.position(today).cashMinor();
  service.exception(change(occurrence,"OVERRIDE",null,null,today.plusDays(3),null));var moved=upcoming(today,today.plusDays(4)).stream().filter(o->o.id().equals(occurrence.id())).findFirst().orElseThrow();assertEquals(today.plusDays(3),moved.date());assertEquals(due,moved.nominalDate());assertEquals(balance,ledger.position(today).cashMinor());assertTrue(ledger.activity(account).isEmpty());assertEquals(base+10000,service.outlook(today,today.plusMonths(6),false,false).household().closingMinor());assertEquals(balance,service.outlook(today,today.plusMonths(6),false,true).household().closingMinor());
 }
 @Test void httpAuthenticationCsrfStrictTypesAndUnknownFieldsRejectWithoutMutation()throws Exception{
  mvc.perform(get("/api/forecast/commitments")).andExpect(status().isUnauthorized());var response=mvc.perform(post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-commitment-password\"}")).andExpect(status().isNoContent()).andReturn().getResponse();var cookie=new Cookie("workspace_session",response.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
  mvc.perform(post("/api/forecast/commitments/batch").cookie(cookie).content("{}")).andExpect(status().isForbidden());
  String valid=json.writeValueAsString(new CommitmentService.Batch(0L,id(),"Synthetic HTTP",List.of(edit(id(),0,today,rule("ONCE",today,"1.00")))));
  for(String bad:List.of(valid.replace("\"revision\":0","\"revision\":\"0\""),valid.replace("\"amountEuro\":\"1.00\"","\"amountEuro\":1"),valid.replace("\"cancelled\":false","\"cancelled\":\"false\""),"{\"unknown\":1}","null","x".repeat(262145)))mvc.perform(post("/api/forecast/commitments/batch").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
  assertEquals(0,rev());assertTrue(service.history().isEmpty());
  mvc.perform(post("/api/forecast/commitments/batch").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content(valid)).andExpect(status().isOk());assertEquals(1,rev());
 }
}
