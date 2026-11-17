package com.householdfinance.workspace;

import com.householdfinance.HouseholdFinanceApplication;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class WorkspaceRestartTest {
    @TempDir Path directory;
    ConfigurableApplicationContext start(String name) {
        return new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE).run(
            "--spring.datasource.url=jdbc:h2:file:"+directory.resolve(name)+";DB_CLOSE_ON_EXIT=FALSE", "--workspace.password=synthetic-restart-workspace-password", "--spring.main.banner-mode=off");
    }
    @Test void recordsSurviveRestartAndEncryptedBackupRecoversIntoANewDatabase() {
        byte[] backup; Map<String,Long> original;
        try(var app=start("original")) {
            var jdbc=app.getBean(JdbcTemplate.class);
            jdbc.update("INSERT INTO ACCOUNTS(ID,NAME,TYPE,BALANCE) VALUES('persist','Synthetic durable account','cash',123.45)");
            var ledger=app.getBean(com.householdfinance.ledger.LedgerService.class);
            var account=ledger.create(new com.householdfinance.ledger.LedgerService.AccountInput("Synthetic durable ledger","CASH",java.time.LocalDate.now(java.time.ZoneId.of("Europe/Dublin")),"20.00","Synthetic restart evidence"));
            ledger.manual(account.id(),new com.householdfinance.ledger.LedgerService.Manual(0L,new com.householdfinance.ledger.LedgerService.Row("durable-entry",account.openingDate(),"Synthetic durable activity","-1.00","BOOKED","ACTIVITY"),"Synthetic source",true));
            app.getBean(com.householdfinance.forecast.ForecastService.class).save(new com.householdfinance.forecast.ForecastService.Input(0L,"Synthetic durable plan",new com.householdfinance.forecast.ForecastService.BaselineInput("MANUAL",account.openingDate(),"20.00","0","Synthetic restart source"),account.openingDate().plusDays(7),"1",java.util.List.of(),true));
            var plans=app.getBean(com.householdfinance.plans.DecisionPlanService.class);
            var p=plans.create(new com.householdfinance.plans.DecisionPlanService.Content("Synthetic durable decision","Synthetic goal",java.util.List.of(new com.householdfinance.plans.DecisionPlanService.Evidence("source","Synthetic source",account.openingDate(),"Synthetic evidence","Estimate")),java.util.List.of(new com.householdfinance.plans.DecisionPlanService.Option("a","Keep cash","Buffer","Rate"),new com.householdfinance.plans.DecisionPlanService.Option("b","Repay","Save interest","Penalty"))));
            plans.choose(p.id(),new com.householdfinance.plans.DecisionPlanService.Choose(p.revision(),"a","Synthetic rationale",account.openingDate().plusDays(7),java.util.List.of(new com.householdfinance.plans.DecisionPlanService.ActionInput("act","Check terms",account.openingDate().plusDays(1))),true));
            var backups=app.getBean(WorkspaceBackupService.class);original=backups.counts();backup=backups.exportBackup("separate-synthetic-backup-passphrase");
        }
        try(var app=start("original")) {
            assertEquals(original,app.getBean(WorkspaceBackupService.class).counts());
            assertEquals("Synthetic durable plan",app.getBean(com.householdfinance.forecast.ForecastService.class).get().plan().inputs().name());
            assertEquals(2,app.getBean(com.householdfinance.plans.DecisionPlanService.class).list().getFirst().data().history().size());
            assertEquals(123.45,app.getBean(JdbcTemplate.class).queryForObject("SELECT BALANCE FROM ACCOUNTS WHERE ID='persist'",Double.class));
        }
        try(var app=start("recovered")) {
            var backups=app.getBean(WorkspaceBackupService.class);
            assertEquals(original,backups.restore(backup,"separate-synthetic-backup-passphrase","RESTORE INTO EMPTY WORKSPACE"));
        }
        try(var app=start("recovered")) {assertEquals("Synthetic rationale",app.getBean(com.householdfinance.plans.DecisionPlanService.class).list().getFirst().data().decision().rationale());assertEquals(original,app.getBean(WorkspaceBackupService.class).counts());assertEquals("Synthetic durable plan",app.getBean(com.householdfinance.forecast.ForecastService.class).get().plan().inputs().name());}
    }
}
