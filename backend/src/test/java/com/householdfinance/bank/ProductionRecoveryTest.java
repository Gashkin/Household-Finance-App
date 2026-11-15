package com.householdfinance.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.ledger.LedgerService;
import com.householdfinance.forecast.ForecastService;
import com.householdfinance.plans.DecisionPlanService;
import com.householdfinance.workspace.WorkspaceBackupService;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
class ProductionRecoveryTest {
  @TempDir Path folder;
  ConfigurableApplicationContext start(String name) {
    return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run(
      "--spring.datasource.url=jdbc:h2:file:"+folder.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE",
      "--workspace.password=synthetic-production-recovery-password","--spring.main.banner-mode=off");
  }
  @Test void consentReviewForecastDecisionRestartAndEncryptedRecoveryFormOneJourney() throws Exception {
    Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwx------"));
    byte[] key=new byte[32]; new java.security.SecureRandom().nextBytes(key);
    byte[] backup; Map<String,Long> counts; String accountId;
    LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));
    try(var provider=new SyntheticProvider("http://127.0.0.1:8080/bank/callback")) {
      provider.environment="PRODUCTION";
      var config=new ProductionConfig(true,provider.app,"unused",provider.callback,folder.resolve("connection.bin").toString(),Base64.getEncoder().encodeToString(key),true);
      var gateway=new SandboxGateway(config,provider.base(),provider.key::getPrivate,new ObjectMapper());
      try(var app=start("original")) {
        var ledger=app.getBean(LedgerService.class); var bank=new SandboxService(gateway,ledger);
        var a=ledger.create(new LedgerService.AccountInput("Synthetic production account","CASH",today,"1000","Synthetic opening statement")); accountId=a.id();
        bank.start(new SandboxService.Start("AIB","IE",true),"synthetic-binding");
        var status=bank.complete(new SandboxService.Complete(provider.state,"synthetic-code"),"synthetic-binding");
        var fetch=bank.fetch(new SandboxService.Fetch(status.accounts().getFirst().handle(),a.id(),today,today,true));
        assertEquals(100000,ledger.position(today).cashMinor());
        var p=fetch.preview();
        ledger.approve(a.id(),p.id(),new LedgerService.Approval(p.revision(),p.proposals().stream().map(LedgerService.Proposal::row).toList(),true));
        var updated=ledger.position(today).accounts().getFirst();
        ledger.reconcile(a.id(),today,new LedgerService.Reconcile(updated.revision(),"990","Synthetic provider booked statement",true,false));
        var forecasts=app.getBean(ForecastService.class);
        var forecast=forecasts.save(new ForecastService.Input(0L,"Synthetic bank-sourced forecast",new ForecastService.BaselineInput("LEDGER",today,null,null,null),today.plusDays(7),"100",List.of(),true));
        assertEquals(98500,forecast.plan().projection().startingMinor()); // pending debit protects EUR 5
        var plans=app.getBean(DecisionPlanService.class);
        var plan=plans.create(new DecisionPlanService.Content("Synthetic sourced decision","Keep a bank-sourced buffer",List.of(new DecisionPlanService.Evidence("bank","Synthetic reconciled ledger",today,"EUR 990 booked, EUR 5 pending","No guarantee of future income")),List.of(new DecisionPlanService.Option("keep","Keep cash","Buffer","Rate"),new DecisionPlanService.Option("repay","Repay","Interest","Penalty"))));
        plans.choose(plan.id(),new DecisionPlanService.Choose(plan.revision(),"keep","Synthetic reviewed cash rationale",today.plusDays(7),List.of(new DecisionPlanService.ActionInput("terms","Check terms",today.plusDays(1))),true));
        var recovery=app.getBean(WorkspaceBackupService.class); counts=recovery.counts(); backup=recovery.exportBackup("synthetic-separate-backup-passphrase");
      }
      try(var app=start("original")) {
        var ledger=app.getBean(LedgerService.class); var bank=new SandboxService(gateway,ledger);
        assertEquals("CONNECTED",bank.status().state()); assertEquals(99000,ledger.position(today).cashMinor());
        var status=bank.status();
        var duplicate=bank.fetch(new SandboxService.Fetch(status.accounts().getFirst().handle(),accountId,today,today,true));
        assertTrue(duplicate.preview().proposals().stream().allMatch(p->p.disposition().equals("DUPLICATE")));
        assertEquals("Synthetic bank-sourced forecast",app.getBean(ForecastService.class).get().plan().inputs().name());
        assertEquals(counts,app.getBean(WorkspaceBackupService.class).counts());
        bank.disconnect();
      }
      try(var app=start("recovered")) {
        assertEquals(counts,app.getBean(WorkspaceBackupService.class).restore(backup,"synthetic-separate-backup-passphrase","RESTORE INTO EMPTY WORKSPACE"));
      }
      try(var app=start("recovered")) {
        assertEquals(99000,app.getBean(LedgerService.class).position(today).cashMinor());
        assertEquals("Synthetic reviewed cash rationale",app.getBean(DecisionPlanService.class).list().getFirst().data().decision().rationale());
        assertEquals(98500,app.getBean(ForecastService.class).get().plan().projection().startingMinor());
      }
    }
  }
}
