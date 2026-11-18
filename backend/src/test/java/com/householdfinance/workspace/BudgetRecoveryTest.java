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
}
