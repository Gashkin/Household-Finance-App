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
