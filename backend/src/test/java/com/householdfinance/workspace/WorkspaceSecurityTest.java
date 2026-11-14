package com.householdfinance.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"workspace.password=synthetic-test-workspace-password", "spring.datasource.url=jdbc:h2:mem:private-security;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class WorkspaceSecurityTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired WorkspaceBackupService backups;
    Cookie session;
    @BeforeEach void prepare() throws Exception {
        List<String> tables=new ArrayList<>(WorkspaceBackupService.TABLES);Collections.reverse(tables);
        for(String table:tables)jdbc.update("DELETE FROM "+table);
        session=unlock();
    }
    Cookie unlock() throws Exception {
        var result=mvc.perform(post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-test-workspace-password\"}")).andExpect(status().isNoContent()).andReturn();
        String header=result.getResponse().getHeader("Set-Cookie");
        assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("SameSite=Strict"));
        return new Cookie("workspace_session",header.split(";",2)[0].split("=",2)[1]);
    }
    MockHttpServletRequestBuilder postJson(String path,Object value) throws Exception {
        return post(path).cookie(session).header("X-Workspace-Request","1").contentType("application/json").content(json.writeValueAsBytes(value));
    }
    @Test void everyFinancialRouteRequiresSessionAndRejectsCrossSiteAndConsole() throws Exception {
        for(String path:List.of("/planner","/imports/history","/reports/monthly-overview","/auth/me","/api/cash-position/latest","/api/evidence-intake/capabilities","/api/workspace/records")) mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("X-Workspace-Locked","1"));
        mvc.perform(get("/planner").cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/api/workspace/lock").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(postJson("/api/workspace/lock",Map.of()).header("Origin","https://attacker.example")).andExpect(status().isForbidden());
        mvc.perform(get("/planner").cookie(session).header("Sec-Fetch-Site","cross-site")).andExpect(status().isForbidden());
        mvc.perform(get("/planner").cookie(session).with(r->{r.setServerName("attacker.example");return r;})).andExpect(status().isForbidden());
        mvc.perform(get("/planner").cookie(session).with(r->{r.setRemoteAddr("192.0.2.50");return r;})).andExpect(status().isForbidden());
        mvc.perform(get("/h2-console/").cookie(session)).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/workspace/lock",Map.of())).andExpect(status().isOk());
        mvc.perform(get("/planner").cookie(session)).andExpect(status().isUnauthorized());
    }
    @Test void encryptedBackupRestoresAllTablesAndPreservesEvidenceIdentity() throws Exception {
        seed();
        Map<String,Long> before=backups.counts();
        byte[] encrypted=mvc.perform(postJson("/api/workspace/backup",Map.of("password","separate-synthetic-backup-passphrase"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
        assertFalse(new String(encrypted,java.nio.charset.StandardCharsets.ISO_8859_1).contains("Synthetic private source"));
        var request=Map.of("password","separate-synthetic-backup-passphrase","backup",Base64.getEncoder().encodeToString(encrypted),"confirmation","RESTORE INTO EMPTY WORKSPACE");
        mvc.perform(postJson("/api/workspace/restore",request)).andExpect(status().isBadRequest());
        assertEquals(before,backups.counts());
        clear();
        mvc.perform(postJson("/api/workspace/restore",request)).andExpect(status().isOk());
        assertEquals(before,backups.counts());
        mvc.perform(get("/api/cash-position/latest").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("snapshot-1")).andExpect(jsonPath("$.liquidCashMinor").value(12345));
        assertEquals("Synthetic private source",jdbc.queryForObject("SELECT PAYLOAD FROM MIGRATION_ARTIFACTS",String.class));
        jdbc.update("INSERT INTO MIGRATION_ARTIFACTS(IMPORT_ID,ARTIFACT_TYPE,CREATED_AT,PAYLOAD) VALUES('next','report',CURRENT_TIMESTAMP,'new')");
        assertEquals(2L,backups.counts().get("MIGRATION_ARTIFACTS"));
    }
    @Test void damagedWrongPasswordIncompleteAndConstraintFailingRestoresLeaveNoPartialData() throws Exception {
        seed();byte[] encrypted=backups.exportBackup("separate-synthetic-backup-passphrase");clear();
        assertThrows(IllegalArgumentException.class,()->backups.restore(encrypted,"wrong-password-123456","RESTORE INTO EMPTY WORKSPACE"));
        byte[] damaged=encrypted.clone();damaged[damaged.length-1]^=1;
        assertThrows(IllegalArgumentException.class,()->backups.restore(damaged,"separate-synthetic-backup-passphrase","RESTORE INTO EMPTY WORKSPACE"));
        byte[] nullArchive=BackupCrypto.encrypt("null".getBytes(java.nio.charset.StandardCharsets.UTF_8),"separate-synthetic-backup-passphrase");
        mvc.perform(postJson("/api/workspace/restore",Map.of("password","separate-synthetic-backup-passphrase","backup",Base64.getEncoder().encodeToString(nullArchive),"confirmation","RESTORE INTO EMPTY WORKSPACE"))).andExpect(status().isBadRequest());
        assertTrue(backups.counts().values().stream().allMatch(n->n==0));
        var archive=json.readValue(BackupCrypto.decrypt(encrypted,"separate-synthetic-backup-passphrase"),WorkspaceBackupService.Archive.class);
        var missing=new LinkedHashMap<>(archive.tables());missing.remove("TRANSACTIONS");
        byte[] incomplete=BackupCrypto.encrypt(json.writeValueAsBytes(new WorkspaceBackupService.Archive(1,archive.createdAt(),missing)),"separate-synthetic-backup-passphrase");
        assertThrows(IllegalArgumentException.class,()->backups.restore(incomplete,"separate-synthetic-backup-passphrase","RESTORE INTO EMPTY WORKSPACE"));
        var invalid=new LinkedHashMap<>(archive.tables());
        var rows=new ArrayList<>(invalid.get("CASH_POSITION_LINES").rows());var broken=new ArrayList<>(rows.get(0));broken.set(invalid.get("CASH_POSITION_LINES").columns().indexOf("SNAPSHOT_ID"),"missing-parent");rows.add(broken);
        invalid.put("CASH_POSITION_LINES",new WorkspaceBackupService.TableData(invalid.get("CASH_POSITION_LINES").columns(),rows));
        byte[] duplicate=BackupCrypto.encrypt(json.writeValueAsBytes(new WorkspaceBackupService.Archive(1,archive.createdAt(),invalid)),"separate-synthetic-backup-passphrase");
        assertThrows(org.springframework.dao.DataAccessException.class,()->backups.restore(duplicate,"separate-synthetic-backup-passphrase","RESTORE INTO EMPTY WORKSPACE"));
        assertTrue(backups.counts().values().stream().allMatch(n->n==0));
    }
    void clear(){List<String> tables=new ArrayList<>(WorkspaceBackupService.TABLES);Collections.reverse(tables);for(String table:tables)jdbc.update("DELETE FROM "+table);}
    void seed(){
        jdbc.update("INSERT INTO ACCOUNTS(ID,NAME,TYPE,BALANCE) VALUES('a','Demo current','cash',123.45)");
        jdbc.update("INSERT INTO CATEGORIES(ID,NAME,BUDGET) VALUES('c','Demo','100')");
        jdbc.update("INSERT INTO COMMITMENTS(ID,TITLE,AMOUNT,DUE_DATE,CATEGORY) VALUES('b','Bill',12.34,'2026-11-20','c')");
        jdbc.update("INSERT INTO MONTHLY_COMMITMENTS(ID,TITLE,AMOUNT,DAY_OF_MONTH,CATEGORY) VALUES('m','Monthly',25,20,'c')");
        jdbc.update("INSERT INTO TRANSACTIONS(ID,TXN_DATE,TITLE,AMOUNT,CATEGORY,TXN_TYPE,SOURCE_MONTHLY_COMMITMENT_ID) VALUES('t','2026-11-14','Demo',25,'c','ACTUAL','m')");
        jdbc.update("INSERT INTO MIGRATION_ARTIFACTS(IMPORT_ID,ARTIFACT_TYPE,CREATED_AT,PAYLOAD) VALUES('i','report',CURRENT_TIMESTAMP,'Synthetic private source')");
        jdbc.update("INSERT INTO CASH_POSITION_SNAPSHOTS(ID,AS_OF_DATE,RECORDED_AT,SOURCE_NOTE,VERIFICATION,MINIMUM_RESERVE_MINOR) VALUES('snapshot-1','2026-11-14',CURRENT_TIMESTAMP,'Synthetic private source','REVIEWED',1000)");
        jdbc.update("INSERT INTO CASH_POSITION_LINES(SNAPSHOT_ID,LINE_ORDER,KIND,LABEL,AMOUNT_MINOR) VALUES('snapshot-1',0,'CASH','Demo cash',12345)");
    }
}
