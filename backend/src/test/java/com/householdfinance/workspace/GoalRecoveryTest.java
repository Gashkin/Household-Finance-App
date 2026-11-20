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
class GoalRecoveryTest {
 @TempDir Path directory;
 ConfigurableApplicationContext start(String name){return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run("--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE","--workspace.password=synthetic-goal-recovery-password","--spring.main.banner-mode=off","--logging.level.root=ERROR");}
 @Test void partialGoalLinkedBillReplaysAndHistorySurviveRestartAndEncryptedRestore(){
   byte[] archive;GoalService.Workspace expected;CommitmentService.State bills;String audit;GoalService.Change input;var day=LocalDate.now(ZoneId.of("Europe/Dublin"));
   try(var app=start("source")){
     app.getBean(AccessService.class).ensureOwner();var ledger=app.getBean(LedgerService.class);var review=app.getBean(ActivityReviewService.class);var goals=app.getBean(GoalService.class);
     String account=ledger.create(new LedgerService.AccountInput("Synthetic durable savings","CASH",day.minusMonths(1),"1000.00","Synthetic statement")).id();String category=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic insurance",null,false,"0.00",null,"Synthetic category")).id();
     input=new GoalService.Change(0L,UUID.randomUUID().toString(),"Synthetic durable annual bill",UUID.randomUUID().toString(),"Synthetic annual insurance","BILL",day.plusMonths(3),1,"300.01","100.01","50.00",account,account,category,true,false,0L);goals.change(input);expected=goals.get();bills=app.getBean(CommitmentService.class).get().state();audit=goals.history().getLast().afterJson;archive=app.getBean(WorkspaceBackupService.class).exportBackup("synthetic-goal-archive-passphrase");
   }
   try(var app=start("source")){assertSaved(app,expected,bills,audit,input);}
   try(var app=start("restored")){app.getBean(AccessService.class).ensureOwner();app.getBean(WorkspaceBackupService.class).restore(archive,"synthetic-goal-archive-passphrase","RESTORE INTO EMPTY WORKSPACE");assertSaved(app,expected,bills,audit,input);}
   try(var app=start("restored")){assertSaved(app,expected,bills,audit,input);}
 }
 void assertSaved(ConfigurableApplicationContext app,GoalService.Workspace expected,CommitmentService.State bills,String audit,GoalService.Change input){var goals=app.getBean(GoalService.class);assertEquals(expected,goals.get());assertEquals(bills,app.getBean(CommitmentService.class).get().state());assertEquals(audit,goals.history().getLast().afterJson);assertEquals(expected.state(),goals.change(input));assertEquals(1,goals.history().size());assertEquals(0,app.getBean(LedgerEntryRepository.class).count());}
}
