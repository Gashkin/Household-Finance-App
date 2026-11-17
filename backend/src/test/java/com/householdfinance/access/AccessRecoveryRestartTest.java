package com.householdfinance.access;

import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.ledger.LedgerService;
import com.householdfinance.workspace.WorkspaceBackupService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class AccessRecoveryRestartTest {
  @TempDir Path directory;
  static final String OLD="synthetic-original-owner-password", FRESH="synthetic-fresh-owner-password", MEMBER="synthetic-private-member-password", ARCHIVE="synthetic-separate-archive-password";
  ConfigurableApplicationContext start(String name,String password) {
    return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run(
        "--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE",
        "--workspace.password="+password,"--spring.main.banner-mode=off");
  }
  @Test void sharedScopeBeyondThirtySourcesSurvivesForecastPublicationRestartAndWithdrawal() {
    var today=LocalDate.now(java.time.ZoneId.of("Europe/Dublin"));
    String memberId,sharedId,snapshotId;
    try(var app=start("shared-scope",OLD)) {
      var access=app.getBean(AccessService.class);access.ensureOwner();
      var ledger=app.getBean(LedgerService.class);
      for(int i=0;i<30;i++) {
        var account=ledger.create(new LedgerService.AccountInput("Synthetic own source "+i,"CASH",today,"1.00","Synthetic opening evidence"));
        account=ledger.manual(account.id(),new LedgerService.Manual(account.revision(),new LedgerService.Row("synthetic-pending-"+i,today,"Synthetic pending debit","-0.01","PENDING","ACTIVITY"),"Synthetic pending evidence",true));
        ledger.reconcile(account.id(),today,new LedgerService.Reconcile(account.revision(),"1.00","Synthetic closing evidence",true,false));
      }
      var invite=access.invite("scopeeditor");access.accept(invite.code(),MEMBER,true);
      var u=access.users().stream().filter(x->x.username().equals("scopeeditor")).findFirst().orElseThrow();memberId=u.id();
      access.change(memberId,new AccessService.Change(u.revision(),Set.of("EDITOR"),"ACTIVE"));
      AccessContext.enter(access.authenticate("scopeeditor",MEMBER));
      var shared=ledger.create(new LedgerService.AccountInput("Synthetic shared source","CASH",today,"2.34","Synthetic shared opening"));sharedId=shared.id();
      shared=ledger.share(sharedId,new LedgerService.Sharing(shared.revision(),true));
      shared=ledger.manual(sharedId,new LedgerService.Manual(shared.revision(),new LedgerService.Row("synthetic-shared-pending",today,"Synthetic shared pending debit","-0.01","PENDING","ACTIVITY"),"Synthetic shared pending evidence",true));
      ledger.reconcile(sharedId,today,new LedgerService.Reconcile(shared.revision(),"2.34","Synthetic shared closing",true,false));
      var hidden=ledger.create(new LedgerService.AccountInput("Synthetic excluded private source","CASH",today,"99.00","Synthetic private opening"));
      ledger.reconcile(hidden.id(),today,new LedgerService.Reconcile(hidden.revision(),"99.00","Synthetic private closing",true,false));
      AccessContext.enter(access.authenticate("owner",OLD));
      var forecast=app.getBean(com.householdfinance.forecast.ForecastService.class);
      var saved=forecast.save(new com.householdfinance.forecast.ForecastService.Input(0L,"Synthetic shared-source plan",new com.householdfinance.forecast.ForecastService.BaselineInput("LEDGER",today,null,null,null),today.plusDays(7),"0",List.of(),true));
      assertEquals(31,saved.plan().inputs().baseline().revisions().size());
      assertEquals(3203,saved.plan().projection().startingMinor());
      var position=ledger.position(today);assertEquals(31,position.accounts().size());
      assertTrue(position.accounts().stream().noneMatch(a->a.name().contains("excluded")));
      var revisions=position.accounts().stream().collect(java.util.stream.Collectors.toMap(LedgerService.AccountView::id,LedgerService.AccountView::revision));
      var snapshot=ledger.publish(new LedgerService.Publish(UUID.randomUUID().toString(),today,revisions,"0",List.of(),true));snapshotId=snapshot.id();
      assertEquals(31,snapshot.cashBalances().size());assertEquals(3234,snapshot.liquidCashMinor());
      assertEquals(31,snapshot.upcomingCommitments().size());assertEquals(31,snapshot.committedMinor());assertEquals(3203,snapshot.availableAfterCommitmentsMinor());
    }
    try(var app=start("shared-scope","")) {
      var access=app.getBean(AccessService.class);AccessContext.enter(access.authenticate("owner",OLD));
      var forecast=app.getBean(com.householdfinance.forecast.ForecastService.class);
      var cash=app.getBean(com.householdfinance.service.CashPositionService.class);
      assertEquals(31,forecast.get().plan().inputs().baseline().revisions().size());
      assertEquals(3203,forecast.get().plan().projection().startingMinor());
      assertEquals(3234,cash.byId(snapshotId).orElseThrow().liquidCashMinor());
      var ledger=app.getBean(LedgerService.class);AccessContext.enter(access.authenticate("scopeeditor",MEMBER));
      var shared=ledger.position(today).accounts().stream().filter(a->a.id().equals(sharedId)).findFirst().orElseThrow();
      ledger.share(sharedId,new LedgerService.Sharing(shared.revision(),false));
      AccessContext.enter(access.authenticate("owner",OLD));
      assertNull(forecast.get().plan());assertNotNull(forecast.get().hiddenPlanRevision());
      assertTrue(cash.byId(snapshotId).isEmpty());
      assertEquals(3000,ledger.position(today).cashMinor());assertEquals(30,ledger.position(today).accounts().size());
    }
  }
  @Test void existingSingleOwnerDatabaseMigratesAccountsToPrivateWithoutLosingBalances() throws Exception {
    try(var app=start("legacy",OLD)) {
      app.getBean(LedgerService.class).create(new LedgerService.AccountInput("Synthetic legacy private","CASH",LocalDate.now(),"23.45","Synthetic legacy evidence"));
    }
    // Reproduce the pre-GBD-16 schema on a disposable file database, then let normal startup upgrade it.
    try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:file:"+directory.resolve("legacy"),"sa","");var sql=connection.createStatement()) {
      for(String statement:List.of("ALTER TABLE LEDGER_ACCOUNTS DROP COLUMN OWNER_ID","ALTER TABLE LEDGER_ACCOUNTS DROP COLUMN SHARED","ALTER TABLE CASH_POSITION_SNAPSHOTS DROP COLUMN OWNER_ID","ALTER TABLE CASH_POSITION_SNAPSHOTS DROP COLUMN SCOPE_REFS","ALTER TABLE DECISION_PLANS DROP COLUMN OWNER_ID","ALTER TABLE LEDGER_AUDIT DROP COLUMN ACTOR_ID","DROP TABLE ACCESS_AUDIT","DROP TABLE HOUSEHOLD_USERS"))sql.execute(statement);
    }
    try(var app=start("legacy",OLD)) {
      var access=app.getBean(AccessService.class);access.ensureOwner();
      var ledger=app.getBean(LedgerService.class);assertEquals(2345,ledger.position(LocalDate.now()).cashMinor());assertFalse(ledger.position(LocalDate.now()).accounts().getFirst().shared());
      assertEquals(0,ledger.sharedPosition(LocalDate.now()).accounts().size());
      var invitation=access.invite("legacyreader");access.accept(invitation.code(),MEMBER,true);
      var u=access.users().stream().filter(x->x.username().equals("legacyreader")).findFirst().orElseThrow();access.change(u.id(),new AccessService.Change(u.revision(),Set.of("READER"),"ACTIVE"));
      AccessContext.enter(access.authenticate("legacyreader",MEMBER));assertEquals(0,ledger.position(LocalDate.now()).accounts().size());
    }
  }
  @Test void namedIdentitySurvivesRestartWithoutBootstrapAndRestoreRequiresMemberReview() {
    byte[] backup; String id,code;
    try(var app=start("original",OLD)) {
      var access=app.getBean(AccessService.class);access.ensureOwner();
      var invitation=access.invite("member");assertTrue(access.accept(invitation.code(),MEMBER,true).accepted());
      var u=access.users().stream().filter(x->x.username().equals("member")).findFirst().orElseThrow();id=u.id();
      assertTrue(access.change(id,new AccessService.Change(u.revision(),Set.of("EDITOR","READER"),"ACTIVE")).accepted());
      var member=access.authenticate("member",MEMBER);AccessContext.enter(member);
      code=access.recoveryCode();
      app.getBean(LedgerService.class).create(new LedgerService.AccountInput("Synthetic member private","CASH",LocalDate.now(),"17.23","Synthetic source"));
      OwnerFixture owner=new OwnerFixture();owner.beforeEach(null);
      backup=app.getBean(WorkspaceBackupService.class).exportBackup(ARCHIVE);
    }
    try(var app=start("original","")) {
      var access=app.getBean(AccessService.class);assertTrue(access.configured());
      assertNotNull(access.authenticate("owner",OLD));assertNotNull(access.authenticate("member",MEMBER));
      assertTrue(access.user(id).roles().contains("EDITOR"));
    }
    try(var app=start("restored",FRESH)) {
      var access=app.getBean(AccessService.class);access.ensureOwner();
      app.getBean(WorkspaceBackupService.class).restore(backup,ARCHIVE,"RESTORE INTO EMPTY WORKSPACE");
      assertNotNull(access.authenticate("owner",FRESH));assertNull(access.authenticate("owner",OLD));
      assertNull(access.authenticate("member",MEMBER));assertEquals("DISABLED",access.user(id).state());
      assertFalse(access.recover("member",code,MEMBER).accepted());
      var u=access.user(id);assertTrue(access.change(id,new AccessService.Change(u.revision(),u.roles(),"ACTIVE")).accepted());
      var member=access.authenticate("member",MEMBER);assertNotNull(member);AccessContext.enter(member);
      assertEquals(1723,app.getBean(LedgerService.class).position(LocalDate.now()).cashMinor());
    }
  }
}
