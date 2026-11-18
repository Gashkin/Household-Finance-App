package com.householdfinance.workspace;
import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.access.*;
import com.householdfinance.ledger.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class BudgetRecoveryTest {
 @TempDir Path directory;
 ConfigurableApplicationContext start(String name){return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run("--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE","--workspace.password=synthetic-budget-recovery-password","--spring.main.banner-mode=off","--logging.level.root=ERROR");}
 @Test void originalsReallocationsActualsAndAuditSurviveRestartAndEncryptedRestore(){
   String id;byte[] archive;BudgetService.Report expected;List<LedgerAudit> history;
   try(var app=start("source")){
     app.getBean(AccessService.class).ensureOwner();var b=app.getBean(BudgetService.class);var review=app.getBean(ActivityReviewService.class);var ledger=app.getBean(LedgerService.class);LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));
     String category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic groceries",null,false,"0.00",null,"Synthetic category")).id();
     var t=b.template(null,new BudgetService.TemplateInput(null,"Synthetic durable template","100.00","CALENDAR",1,List.of(new BudgetService.LineInput(category,"100.00",true,null)),"Synthetic initial allocation"));var p=b.instantiate(t.id(),new BudgetService.Instantiate(0L,today,"Synthetic period"));id=p.id();
     b.change(id,new BudgetService.Change(0L,"90.00",List.of(new BudgetService.LineInput(category,"80.00",true,today)),"Synthetic lower-income choice"));
     var account=ledger.create(new LedgerService.AccountInput("Synthetic durable cash","CASH",today,"100.00","Synthetic statement"));ledger.manual(account.id(),new LedgerService.Manual(0L,new LedgerService.Row("purchase",today,"Synthetic groceries","-1.01","BOOKED","ACTIVITY"),"Synthetic receipt",true));review.classify(ledger.activity(account.id()).getFirst().id,new ActivityReviewService.Classification(1L,category,"CONFIRMED","Synthetic confirmation",false));
     expected=b.report(id);history=b.history(id);archive=app.getBean(WorkspaceBackupService.class).exportBackup("synthetic-budget-archive-passphrase");assertEquals(101,expected.actualMinor());assertEquals(10000,expected.rows().getFirst().originalMinor());assertEquals(1000,expected.unallocatedMinor());
   }
   try(var app=start("source")){assertEquals(expected,app.getBean(BudgetService.class).report(id));assertEquals(history.size(),app.getBean(BudgetService.class).history(id).size());}
   try(var app=start("restored")){app.getBean(AccessService.class).ensureOwner();app.getBean(WorkspaceBackupService.class).restore(archive,"synthetic-budget-archive-passphrase","RESTORE INTO EMPTY WORKSPACE");assertEquals(expected,app.getBean(BudgetService.class).report(id));assertEquals(history.getLast().afterJson,app.getBean(BudgetService.class).history(id).getLast().afterJson);}
   try(var app=start("restored")){assertEquals(expected,app.getBean(BudgetService.class).report(id));}
 }
 @Test void correctedCarryIncomeReversalAndLegacyPayloadsSurviveRestartAndEncryptedRecovery()throws Exception{
   String id;byte[] archive;BudgetService.Report expected;
   try(var app=start("corrected-source")){
     app.getBean(AccessService.class).ensureOwner();var budgets=app.getBean(BudgetService.class);var review=app.getBean(ActivityReviewService.class);var ledger=app.getBean(LedgerService.class);var json=app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));
     String category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic durable carry",null,false,"0.00",null,"Synthetic category")).id();var t=budgets.template(null,new BudgetService.TemplateInput(null,"Synthetic carry template","100.00","CALENDAR",1,List.of(new BudgetService.LineInput(category,"100.00",true,null)),"Synthetic initial plan"));
     var previous=budgets.instantiate(t.id(),new BudgetService.Instantiate(0L,today.minusMonths(1),"Synthetic prior period"));var current=budgets.instantiate(t.id(),new BudgetService.Instantiate(0L,today,"Synthetic carried period"));id=current.id();
     // Precisely model the previously published version-7 JSON payloads, which lack this optional field.
     for(String periodId:List.of(previous.id(),current.id())){var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(jdbc.queryForObject("SELECT PAYLOAD FROM LEDGER_REVIEW_RECORDS WHERE ID=?",String.class,periodId));payload.remove("correctedRollover");jdbc.update("UPDATE LEDGER_REVIEW_RECORDS SET PAYLOAD=? WHERE ID=?",json.writeValueAsString(payload),periodId);}
     assertNull(budgets.report(id).period().correctedRollover());assertEquals(10000,budgets.report(id).rows().getFirst().rolloverMinor());
     budgets.change(id,new BudgetService.Change(0L,"0.00",List.of(new BudgetService.LineInput(category,"0.00",true,null)),"Synthetic zero new allocation"));
     var account=ledger.create(new LedgerService.AccountInput("Synthetic carry cash","CASH",today.minusYears(1),"1000.00","Synthetic statement"));String salary=null;long revision=0;
     for(var row:List.of(new LedgerService.Row("late-expense",previous.start(),"Synthetic late expense","-100.00","BOOKED","ACTIVITY"),new LedgerService.Row("salary",previous.start(),"Synthetic salary","100.00","BOOKED","ACTIVITY"),new LedgerService.Row("income-reversal",current.start(),"Synthetic income reversal","-30.00","BOOKED","ACTIVITY"))){ledger.manual(account.id(),new LedgerService.Manual(revision++,row,"Synthetic reviewed source",true));var entry=ledger.activity(account.id()).stream().filter(e->e.reference.equals(row.reference())).findFirst().orElseThrow();review.classify(entry.id,new ActivityReviewService.Classification(revision++,category,"CONFIRMED","Synthetic reviewed category",false));if(row.reference().equals("salary"))salary=entry.id;}
     var reversal=ledger.activity(account.id()).stream().filter(e->e.reference.equals("income-reversal")).findFirst().orElseThrow();review.link(reversal.id,new ActivityReviewService.Link(revision,salary,revision,"REVERSAL","Synthetic reversed income"));
     var preview=budgets.report(id).carryPreview();budgets.correctRollover(id,new BudgetService.CarryCorrection(1L,preview.predecessorRevision(),preview.amountsMinor(),"Synthetic durable carry correction"));expected=budgets.report(id);assertEquals(10000,expected.period().rollover().get(category));assertEquals(0,expected.rows().getFirst().rolloverMinor());assertEquals(-3000,expected.confirmedIncomeMinor());archive=app.getBean(WorkspaceBackupService.class).exportBackup("synthetic-corrected-carry-archive");
   }
   try(var app=start("corrected-source")){assertCorrected(app,id,expected);}
   try(var app=start("corrected-restored")){app.getBean(AccessService.class).ensureOwner();app.getBean(WorkspaceBackupService.class).restore(archive,"synthetic-corrected-carry-archive","RESTORE INTO EMPTY WORKSPACE");assertCorrected(app,id,expected);}
   try(var app=start("corrected-restored")){assertCorrected(app,id,expected);}
 }
 void assertCorrected(ConfigurableApplicationContext app,String id,BudgetService.Report expected){var budgets=app.getBean(BudgetService.class);assertEquals(expected,budgets.report(id));var history=budgets.history(id);assertEquals(3,history.size());assertEquals("BUDGET_ROLLOVER",history.getLast().action);assertEquals("owner",history.getLast().actorId);assertEquals("Synthetic durable carry correction",history.getLast().source);assertTrue(history.getLast().beforeJson.contains("10000"));assertTrue(history.getLast().afterJson.contains("correctedRollover"));}
}
