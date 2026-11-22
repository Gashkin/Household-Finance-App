package com.householdfinance.workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ActivityReviewRecoveryTest {
 @TempDir Path directory;
 static final String PASSWORD="synthetic-review-recovery-password",ARCHIVE="synthetic-review-archive-passphrase";
 LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));
 ConfigurableApplicationContext start(String name){return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run("--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE","--workspace.password="+PASSWORD,"--spring.main.banner-mode=off","--logging.level.root=ERROR");}
 @Test void reviewRulesLinksProtectionAndAuditSurviveRestartAndEncryptedRecovery()throws Exception{
   byte[] backup;String account;Map<String,Long> counts;
   try(var app=start("source")){
     app.getBean(AccessService.class).ensureOwner();var ledger=app.getBean(LedgerService.class);var review=app.getBean(ActivityReviewService.class);
     var a=ledger.create(new LedgerService.AccountInput("Synthetic durable cash","CASH",today,"100.00","Synthetic statement"));account=a.id();
     ledger.manual(account,new LedgerService.Manual(0L,new LedgerService.Row("original",today,"Synthetic purchase","-10.00","BOOKED","ACTIVITY"),"Synthetic receipt",true));
     var entry=ledger.activity(account).getFirst();var category=review.category(null,new ActivityReviewService.CategoryInput(null,"Interest & Fees",null,false,"20.00",null,"Synthetic category"));
     review.classify(entry.id,new ActivityReviewService.Classification(1L,category.id(),"CONFIRMED","Synthetic receipt confirmation",true));var rule=review.rules().getFirst();review.approveRule(rule.id(),new ActivityReviewService.RuleApproval(0L,true,"Synthetic explicit approval"));
     ledger.manual(account,new LedgerService.Manual(2L,new LedgerService.Row("refund",today,"Synthetic partial reversal","4.00","BOOKED","ACTIVITY"),"Synthetic refund source",true));
     var refund=ledger.activity(account).stream().filter(e->e.reference.equals("refund")).findFirst().orElseThrow();review.link(refund.id,new ActivityReviewService.Link(3L,entry.id,3L,"REFUND","Synthetic refund link"));
     ledger.reconcile(account,today,new LedgerService.Reconcile(4L,"94.00","Synthetic closing statement",true,false));
     review.complete(new ActivityReviewService.Completion(today,"Synthetic guided review",true,review.visibleAuditCount()));
     var backups=app.getBean(WorkspaceBackupService.class);counts=backups.counts();backup=backups.exportBackup(ARCHIVE);
   }
   try(var app=start("source")){assertState(app,account);}
   try(var app=start("restored")){app.getBean(AccessService.class).ensureOwner();assertEquals(counts,app.getBean(WorkspaceBackupService.class).restore(backup,ARCHIVE,"RESTORE INTO EMPTY WORKSPACE"));assertState(app,account);}
   try(var app=start("restored")){assertState(app,account);}
 }
 void assertState(ConfigurableApplicationContext app,String account){
   var review=app.getBean(ActivityReviewService.class);var ledger=app.getBean(LedgerService.class);
   assertTrue(review.rules().getFirst().approved());assertEquals("Interest & Fees",review.categories().getFirst().name());assertEquals(1,review.guide(today).completedReviews().size());assertEquals(600,ledger.position(today).spendingMinor());assertTrue(ledger.position(today).accounts().getFirst().reconciled());
   assertThrows(IllegalArgumentException.class,()->ledger.manual(account,new LedgerService.Manual(4L,new LedgerService.Row("late",today,"Synthetic late entry","-1.00","BOOKED","ACTIVITY"),"Synthetic late source",true)));assertTrue(ledger.history(account).stream().allMatch(h->h.actorId.equals("owner")));
   assertReplay(ledger,account,"original","Synthetic purchase","-10.00");assertReplay(ledger,account,"refund","Synthetic partial reversal","4.00");
 }
 void assertReplay(LedgerService ledger,String account,String reference,String description,String amount){
   var before=ledger.activity(account).stream().filter(e->e.reference.equals(reference)).findFirst().orElseThrow();assertEquals("ACTIVITY",before.kind);
   var preview=ledger.preview(account,new LedgerService.PreviewInput("reference,date,description,amount,status,kind\n"+reference+","+today+","+description+","+amount+",BOOKED,ACTIVITY\n","Synthetic durable identical source"));assertEquals("DUPLICATE",preview.proposals().getFirst().disposition());
   assertEquals(0,ledger.approve(account,preview.id(),new LedgerService.Approval(preview.revision(),List.of(preview.proposals().getFirst().row()),true)).added());var after=ledger.activity(account).stream().filter(e->e.id.equals(before.id)).findFirst().orElseThrow();assertEquals(before.linkedEntryId,after.linkedEntryId);assertEquals(before.linkType,after.linkType);assertEquals(before.categoryId,after.categoryId);assertEquals(before.reviewState,after.reviewState);
 }
 @Test void transfersAndCardSettlementsRetainOriginalReplayIdentityAfterFileRestart(){
   for(String type:List.of("TRANSFER","CARD_SETTLEMENT")){
     String left,right;
     try(var app=start(type)){
       var ledger=app.getBean(LedgerService.class);var review=app.getBean(ActivityReviewService.class);
       left=ledger.create(new LedgerService.AccountInput("Synthetic left "+type,"CASH",today,"100.00","Synthetic source")).id();right=ledger.create(new LedgerService.AccountInput("Synthetic right "+type,type.equals("TRANSFER")?"CASH":"DEBT",today,"100.00","Synthetic source")).id();
       ledger.manual(left,new LedgerService.Manual(0L,new LedgerService.Row("left",today,"Synthetic left source","-10.00","BOOKED","ACTIVITY"),"Synthetic receipt",true));ledger.manual(right,new LedgerService.Manual(0L,new LedgerService.Row("right",today,"Synthetic right source","10.00","BOOKED","ACTIVITY"),"Synthetic receipt",true));
       review.link(ledger.activity(left).getFirst().id,new ActivityReviewService.Link(1L,ledger.activity(right).getFirst().id,1L,type,"Synthetic related source evidence"));
       ledger.reconcile(left,today,new LedgerService.Reconcile(2L,"90.00","Synthetic left statement",true,false));ledger.reconcile(right,today,new LedgerService.Reconcile(2L,"110.00","Synthetic right statement",true,false));
     }
     try(var app=start(type)){var ledger=app.getBean(LedgerService.class);assertReplay(ledger,left,"left","Synthetic left source","-10.00");assertReplay(ledger,right,"right","Synthetic right source","10.00");assertEquals(0,ledger.position(today).spendingMinor());assertEquals(0,ledger.position(today).incomeMinor());assertEquals(20000,ledger.position(today).netMinor());}
   }
 }
 @Test void actualVersionSixArchiveRestoresAsProvisionalWithoutInventedClassifications()throws Exception{
   byte[] v6;
   try(var app=start("v6-source")){
     app.getBean(AccessService.class).ensureOwner();var ledger=app.getBean(LedgerService.class);var a=ledger.create(new LedgerService.AccountInput("Synthetic old cash","CASH",today,"100.00","Synthetic old source"));ledger.manual(a.id(),new LedgerService.Manual(0L,new LedgerService.Row("old",today,"Synthetic old debit","-1.00","BOOKED","ACTIVITY"),"Synthetic old receipt",true));
     var json=app.getBean(ObjectMapper.class);var current=json.readValue(BackupCrypto.decrypt(app.getBean(WorkspaceBackupService.class).exportBackup(ARCHIVE),ARCHIVE),WorkspaceBackupService.Archive.class);v6=BackupCrypto.encrypt(json.writeValueAsBytes(new WorkspaceBackupService.Archive(6,current.createdAt(),LegacyArchiveFixture.tables(current,6))),ARCHIVE);
   }
   try(var app=start("v6-restored")){app.getBean(AccessService.class).ensureOwner();app.getBean(WorkspaceBackupService.class).restore(v6,ARCHIVE,"RESTORE INTO EMPTY WORKSPACE");var review=app.getBean(ActivityReviewService.class);assertTrue(review.categories().isEmpty());assertEquals("PROVISIONAL",review.queue(null,today,today,null,null).getFirst().entry().reviewState);}
 }
 @Test void versionThreeDatabaseUpgradesWithoutDiscardingSourceFacts()throws Exception{
   String url="jdbc:h2:file:"+directory.resolve("v3")+";DB_CLOSE_ON_EXIT=FALSE";org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("3").load().migrate();
   try(var connection=java.sql.DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()){sql.executeUpdate("INSERT INTO LEDGER_ACCOUNTS(ID,NAME,KIND,OPENING_DATE,OPENING_MINOR,SOURCE,REVISION) VALUES('synthetic-v3','Synthetic existing cash','CASH',CURRENT_DATE,10000,'Synthetic V3 source',0)");sql.executeUpdate("INSERT INTO LEDGER_ENTRIES(ID,ACCOUNT_ID,REFERENCE,ACTIVITY_DATE,DESCRIPTION,AMOUNT_MINOR,STATUS,KIND,SOURCE) VALUES('synthetic-old-entry','synthetic-v3','old-ref',CURRENT_DATE,'Synthetic retained debit',-100,'BOOKED','ACTIVITY','Synthetic retained receipt')");}
   try(var app=start("v3")){var e=app.getBean(LedgerService.class).activity("synthetic-v3").getFirst();assertEquals("PROVISIONAL",e.reviewState);assertNull(e.categoryId);assertEquals(-100,e.amountMinor);assertEquals("Synthetic retained receipt",e.source);}
 }
}
