package com.householdfinance.workspace;
import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.access.*;
import com.householdfinance.ledger.*;
import com.householdfinance.forecast.ForecastService;
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
class CommitmentRecoveryTest {
 @TempDir Path directory;
 ConfigurableApplicationContext start(String name){return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run("--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE","--workspace.password=synthetic-commitment-recovery-password","--spring.main.banner-mode=off","--logging.level.root=ERROR");}
 @Test void scheduleVersionsOverridesMatchesAndSavedPlansSurviveRestartAndEncryptedRestore(){
  byte[] archive;CommitmentService.State expected;List<CommitmentService.Occurrence> occurrences;LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));String lastAudit,operation;
  try(var app=start("source")){
   app.getBean(AccessService.class).ensureOwner();var service=app.getBean(CommitmentService.class);var ledger=app.getBean(LedgerService.class);var review=app.getBean(ActivityReviewService.class);
   String account=ledger.create(new LedgerService.AccountInput("Synthetic durable account","CASH",today,"100.00","Synthetic statement")).id();String category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic durable category",null,false,"0.00",null,"Synthetic reason")).id();String id=UUID.randomUUID().toString();operation=UUID.randomUUID().toString();
   var rule=new CommitmentService.Rule("Synthetic durable bill",account,category,"EXPENSE","EXPECTED","ESTIMATED","10.00","MONTHLY",today,null,null,0,null);
   service.batch(new CommitmentService.Batch(0L,operation,"Synthetic durable schedule",List.of(new CommitmentService.Edit(id,0L,today,false,rule))));
   ledger.manual(account,new LedgerService.Manual(0L,new LedgerService.Row("durable-paid",today,"Synthetic bill","-9.50","BOOKED","ACTIVITY"),"Synthetic receipt",true));var actual=ledger.activity(account).getFirst();review.classify(actual.id,new ActivityReviewService.Classification(1L,category,"CONFIRMED","Synthetic approval",false));
   var first=service.upcoming(today,today).getFirst();service.exception(new CommitmentService.ExceptionInput(1L,UUID.randomUUID().toString(),"Synthetic durable match",first.id(),null,null,false,actual.id,"MATCH",2L));
   var future=service.upcoming(today.plusDays(1),today.plusMonths(2)).getFirst();service.exception(new CommitmentService.ExceptionInput(2L,UUID.randomUUID().toString(),"Synthetic durable override",future.id(),future.date().plusDays(2),"11.01",false,null,"OVERRIDE",null));
   var forecast=app.getBean(ForecastService.class);forecast.save(new ForecastService.Input(0L,"Synthetic retained legacy plan",new ForecastService.BaselineInput("MANUAL",today,"90.50","0.00","Synthetic reviewed estimate"),today.plusMonths(6),"0.00",List.of(),true));
   expected=service.get().state();occurrences=service.upcoming(today,today.plusMonths(6));lastAudit=service.history().getLast().afterJson;archive=app.getBean(WorkspaceBackupService.class).exportBackup("synthetic-commitment-archive-passphrase");
  }
  try(var app=start("source")){assertSaved(app,expected,occurrences,lastAudit,operation,today);}
  try(var app=start("restored")){app.getBean(AccessService.class).ensureOwner();app.getBean(WorkspaceBackupService.class).restore(archive,"synthetic-commitment-archive-passphrase","RESTORE INTO EMPTY WORKSPACE");assertSaved(app,expected,occurrences,lastAudit,operation,today);}
  try(var app=start("restored")){assertSaved(app,expected,occurrences,lastAudit,operation,today);}
 }
 void assertSaved(ConfigurableApplicationContext app,CommitmentService.State expected,List<CommitmentService.Occurrence> occurrences,String audit,String operation,LocalDate today){var service=app.getBean(CommitmentService.class);assertEquals(expected,service.get().state());assertEquals(occurrences,service.upcoming(today,today.plusMonths(6)));assertEquals(audit,service.history().getLast().afterJson);assertTrue(service.operation(operation).get("completed"));assertEquals(today.plusMonths(6),app.getBean(ForecastService.class).get().plan().inputs().endDate());assertEquals(1,app.getBean(LedgerEntryRepository.class).count());}
}
