package com.householdfinance.workspace;

import com.householdfinance.HouseholdFinanceApplication;
import java.nio.file.Path;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class SchemaMigrationTest {
    @TempDir Path directory;
    String url() { return "jdbc:h2:file:"+directory.resolve("synthetic-schema")+";DB_CLOSE_ON_EXIT=FALSE"; }
    ConfigurableApplicationContext start() {
        var application=new SpringApplication(HouseholdFinanceApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        return application.run("--spring.datasource.url="+url(),"--workspace.password=synthetic-migration-workspace-password",
            "--bank.production.enabled=false","--bank.sandbox.enabled=false","--evidence.ai.enabled=false",
            "--spring.main.banner-mode=off","--logging.level.root=OFF");
    }
    void sql(String query) throws Exception {
        try(var connection=DriverManager.getConnection(url(),"sa","");var statement=connection.createStatement()) { statement.execute(query); }
    }
    void assertMoneyKept() throws Exception {
        try(var connection=DriverManager.getConnection(url(),"sa","");var statement=connection.createStatement();
            var result=statement.executeQuery("SELECT BALANCE FROM ACCOUNTS WHERE ID='synthetic'")) {
            assertTrue(result.next());assertEquals(12.34,result.getDouble(1));
        }
    }
    void seedVersioned() throws Exception {
        try(var context=start()) {
            context.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                .update("INSERT INTO ACCOUNTS(ID,NAME,TYPE,BALANCE) VALUES('synthetic','Synthetic cash','Cash',12.34)");
        }
    }
    @Test void unversionedDatabaseIsNeverAutomaticallyAdoptedOrChanged() throws Exception {
        sql("CREATE TABLE ACCOUNTS(ID VARCHAR(64) PRIMARY KEY,NAME VARCHAR(128),TYPE VARCHAR(64),BALANCE DOUBLE)");
        sql("INSERT INTO ACCOUNTS VALUES('synthetic','Synthetic legacy cash','Cash',12.34)");
        assertThrows(RuntimeException.class,()->{try(var ignored=start()) {fail("Unversioned data must block startup");}});
        assertMoneyKept();
    }
    @Test void newerSchemaRejectsOlderApplicationAndKeepsRecords() throws Exception {
        seedVersioned();
        sql("UPDATE \"flyway_schema_history\" SET \"version\"='999' WHERE \"version\"='1'");
        assertThrows(RuntimeException.class,()->{try(var ignored=start()) {fail("Future schema must block startup");}});
        assertMoneyKept();
    }
    @Test void changedMigrationChecksumBlocksStartupWithoutTouchingMoney() throws Exception {
        seedVersioned();
        sql("UPDATE \"flyway_schema_history\" SET \"checksum\"=\"checksum\"+1 WHERE \"version\"='1'");
        assertThrows(RuntimeException.class,()->{try(var ignored=start()) {fail("Changed history must block startup");}});
        assertMoneyKept();
    }
}
