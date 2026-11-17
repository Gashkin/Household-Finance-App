package com.householdfinance.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.access.*;
import com.householdfinance.member.*;
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
class MemberRecoveryTest {
  @TempDir Path directory;
  static final String PASSWORD="synthetic-member-recovery-password",ARCHIVE="synthetic-member-archive-passphrase";
  ConfigurableApplicationContext start(String name){return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run("--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE","--workspace.password="+PASSWORD,"--spring.main.banner-mode=off","--logging.level.root=ERROR");}
  @Test void memberDecisionsPreferencesAndHistorySurviveRestartAndVersionSixRecovery()throws Exception{
    byte[] backup;String id=UUID.randomUUID().toString();Map<String,Long> counts;
    try(var app=start("original")){
      app.getBean(AccessService.class).ensureOwner();var members=app.getBean(MemberService.class);
      members.create(new MemberService.NewPurchase(id,"Synthetic durable purchase","Home","12.34",LocalDate.now(ZoneId.of("Europe/Dublin")),false,members.report().token(),true));
      members.change(id,new MemberService.Change(1L,"BOUGHT",null,null,null,members.report().token()));
      members.preferences(new MemberService.PreferenceInput(0L,List.of("Garden"),"COMPACT",false,false));
      var backups=app.getBean(WorkspaceBackupService.class);counts=backups.counts();backup=backups.exportBackup(ARCHIVE);
      assertTrue(counts.get("MEMBER_RECORDS")>=4);
    }
    try(var app=start("original")){
      var members=app.getBean(MemberService.class);assertEquals("BOUGHT",members.get().purchases().getFirst().purchase().state());assertEquals("COMPACT",members.get().preferences().values().dashboard());assertEquals(2,members.history(id).size());
    }
    try(var app=start("restored")){
      app.getBean(AccessService.class).ensureOwner();assertEquals(counts,app.getBean(WorkspaceBackupService.class).restore(backup,ARCHIVE,"RESTORE INTO EMPTY WORKSPACE"));
      var members=app.getBean(MemberService.class);assertEquals(1234,members.get().purchases().getFirst().purchase().amountMinor());assertEquals(2,members.history(id).size());
    }
    try(var app=start("restored")){assertEquals("Garden",app.getBean(MemberService.class).get().preferences().values().categories().getFirst());}
  }
  @Test void actualVersionFiveArchiveRemainsRecoverableWithoutMemberTables()throws Exception{
    byte[] v5;
    try(var app=start("v5-source")){
      app.getBean(AccessService.class).ensureOwner();var json=app.getBean(ObjectMapper.class);var backup=app.getBean(WorkspaceBackupService.class).exportBackup(ARCHIVE);
      var current=json.readValue(BackupCrypto.decrypt(backup,ARCHIVE),WorkspaceBackupService.Archive.class);
      Map<String,WorkspaceBackupService.TableData> tables=LegacyArchiveFixture.tables(current,5);
      v5=BackupCrypto.encrypt(json.writeValueAsBytes(new WorkspaceBackupService.Archive(5,current.createdAt(),tables)),ARCHIVE);
    }
    try(var app=start("v5-restored")){
      app.getBean(AccessService.class).ensureOwner();var counts=app.getBean(WorkspaceBackupService.class).restore(v5,ARCHIVE,"RESTORE INTO EMPTY WORKSPACE");assertEquals(0,counts.get("MEMBER_RECORDS"));assertTrue(app.getBean(MemberService.class).get().purchases().isEmpty());
    }
  }
  @Test void versionTwoDatabaseUpgradesAdditivelyWithoutChangingExistingLedger()throws Exception{
    String url="jdbc:h2:file:"+directory.resolve("v2")+";DB_CLOSE_ON_EXIT=FALSE";
    org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("2").load().migrate();
    try(var connection=java.sql.DriverManager.getConnection(url,"sa","");var sql=connection.prepareStatement("INSERT INTO LEDGER_ACCOUNTS(ID,NAME,KIND,OPENING_DATE,OPENING_MINOR,SOURCE,REVISION) VALUES('synthetic-v2','Synthetic retained source','CASH',?,12345,'Synthetic V2 evidence',0)")){
      sql.setObject(1,LocalDate.now(ZoneId.of("Europe/Dublin")));sql.executeUpdate();
    }
    try(var app=start("v2")){
      assertEquals(12345,app.getBean(com.householdfinance.ledger.LedgerService.class).position(LocalDate.now(ZoneId.of("Europe/Dublin"))).cashMinor());
      assertEquals(0,app.getBean(MemberRepository.class).count());
    }
  }
}
