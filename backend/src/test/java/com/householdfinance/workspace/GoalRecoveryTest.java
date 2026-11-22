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
 @Test void reviewInteractionsRemainConsistentThroughRestartRestoreAndReactivation(){
   byte[] archive;GoalService.Workspace expected;CommitmentService.State bills;String cancelledId,activeCategory,account;List<String> audits;var day=LocalDate.now(ZoneId.of("Europe/Dublin"));
   try(var app=start("review-source")){
     app.getBean(AccessService.class).ensureOwner();var goals=app.getBean(GoalService.class);var ledger=app.getBean(LedgerService.class);var review=app.getBean(ActivityReviewService.class);var commits=app.getBean(CommitmentService.class);
     account=ledger.create(new LedgerService.AccountInput("Synthetic recovery cash","CASH",day.minusMonths(1),"1000.00","Synthetic statement")).id();String retired=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic retired insurance",null,false,"0.00",null,"Synthetic category")).id(),paid=UUID.randomUUID().toString();
     goals.change(new GoalService.Change(0L,UUID.randomUUID().toString(),"Synthetic original paid goal",paid,"Paid","BILL",day,1,"100.00","100.00","10.00",account,account,retired,true,false,0L));ledger.manual(account,new LedgerService.Manual(0L,new LedgerService.Row("review-paid",day,"Synthetic paid bill","-100.00","BOOKED","ACTIVITY"),"Synthetic statement",true));var entry=ledger.activity(account).getFirst();review.classify(entry.id,new ActivityReviewService.Classification(1L,retired,"CONFIRMED","Synthetic approval",false));commits.exception(new CommitmentService.ExceptionInput(1L,UUID.randomUUID().toString(),"Synthetic paid match",paid+"/"+day,null,null,false,entry.id,"MATCH",2L));review.category(retired,new ActivityReviewService.CategoryInput(0L,"Synthetic retired insurance",null,true,"0.00",null,"Synthetic retirement"));goals.change(new GoalService.Change(1L,UUID.randomUUID().toString(),"Synthetic retired-category release",paid,"Paid","BILL",day,1,"100.00","0.00","0.00",account,account,retired,true,false,2L));
     activeCategory=review.category(null,new ActivityReviewService.CategoryInput(null,"Synthetic active insurance",null,false,"0.00",null,"Synthetic category")).id();String changed=UUID.randomUUID().toString();goals.change(new GoalService.Change(2L,UUID.randomUUID().toString(),"Synthetic future bill",changed,"Changed","BILL",day.plusDays(30),2,"200.00","20.00","10.00",account,account,activeCategory,true,false,2L));commits.batch(new CommitmentService.Batch(3L,UUID.randomUUID().toString(),"Synthetic external income edit",List.of(new CommitmentService.Edit(changed,1L,day,false,new CommitmentService.Rule("Changed",account,activeCategory,"INCOME","EXPECTED","ESTIMATED","200.00","ONCE",day.plusDays(30),null,null,0,null)))));
     cancelledId=UUID.randomUUID().toString();goals.change(new GoalService.Change(3L,UUID.randomUUID().toString(),"Synthetic cancellable bill",cancelledId,"Cancelled","BILL",day.plusDays(60),3,"300.00","30.00","10.00",account,account,activeCategory,true,false,4L));String closing=cancelledId,category=activeCategory,cash=account;var saved=goals.get().state();var before=commits.get().state();assertThrows(IllegalArgumentException.class,()->goals.change(new GoalService.Change(4L,UUID.randomUUID().toString(),"Synthetic rejected changed cancellation",closing,"Cancelled","BILL",day.plusDays(60),3,"400.00","30.00","10.00",cash,cash,category,true,true,5L)));assertEquals(saved,goals.get().state());assertEquals(before,commits.get().state());goals.change(new GoalService.Change(4L,UUID.randomUUID().toString(),"Synthetic unchanged cancellation",cancelledId,"Cancelled","BILL",day.plusDays(60),3,"300.00","30.00","10.00",account,account,activeCategory,true,true,5L));
     expected=goals.get();assertTrue(expected.rows().stream().filter(r->r.goal().id().equals(changed)).findFirst().orElseThrow().linkedBillConflict());bills=commits.get().state();audits=goals.history().stream().map(a->a.afterJson).toList();archive=app.getBean(WorkspaceBackupService.class).exportBackup("synthetic-goal-review-recovery-passphrase");
   }
   try(var app=start("review-source")){assertReviewSnapshot(app,expected,bills,audits);}
   try(var app=start("review-restored")){app.getBean(AccessService.class).ensureOwner();app.getBean(WorkspaceBackupService.class).restore(archive,"synthetic-goal-review-recovery-passphrase","RESTORE INTO EMPTY WORKSPACE");assertReviewSnapshot(app,expected,bills,audits);}
   try(var app=start("review-restored")){assertReviewSnapshot(app,expected,bills,audits);var goals=app.getBean(GoalService.class);var commits=app.getBean(CommitmentService.class);goals.change(new GoalService.Change(expected.state().revision(),UUID.randomUUID().toString(),"Synthetic restored reactivation",cancelledId,"Cancelled","BILL",day.plusDays(60),3,"300.00","30.00","10.00",account,account,activeCategory,true,false,bills.revision()));assertEquals(1,commits.upcoming(day,day.plusMonths(6)).stream().filter(o->o.scheduleId().equals(cancelledId)).count());assertFalse(goals.get().rows().stream().filter(r->r.goal().id().equals(cancelledId)).findFirst().orElseThrow().linkedBillConflict());assertEquals(90000,app.getBean(LedgerService.class).position(day).cashMinor());}
 }
 void assertReviewSnapshot(ConfigurableApplicationContext app,GoalService.Workspace expected,CommitmentService.State bills,List<String> audits){var goals=app.getBean(GoalService.class);assertEquals(expected,goals.get());assertEquals(bills,app.getBean(CommitmentService.class).get().state());assertEquals(audits,goals.history().stream().map(a->a.afterJson).toList());assertEquals(90000,app.getBean(LedgerService.class).position(expected.today()).cashMinor());assertEquals(1,app.getBean(LedgerEntryRepository.class).count());}
}
