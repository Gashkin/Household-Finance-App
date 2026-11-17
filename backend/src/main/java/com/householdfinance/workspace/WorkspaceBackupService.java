package com.householdfinance.workspace;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceBackupService {
    // Fixed dependency order, controlled by code. Names in a backup never become SQL identifiers.
    static final List<String> V1_TABLES = List.of("ACCOUNTS", "CATEGORIES", "COMMITMENTS", "MONTHLY_COMMITMENTS", "TRANSACTIONS", "MIGRATION_ARTIFACTS", "CASH_POSITION_SNAPSHOTS", "CASH_POSITION_LINES");
    static final List<String> V2_TABLES = java.util.stream.Stream.concat(V1_TABLES.stream(), java.util.stream.Stream.of("LEDGER_ACCOUNTS","LEDGER_ENTRIES","LEDGER_AUDIT")).toList();
    static final List<String> V3_TABLES = java.util.stream.Stream.concat(V2_TABLES.stream(), java.util.stream.Stream.of("FORECAST_PLANS")).toList();
    static final List<String> TABLES = java.util.stream.Stream.concat(V3_TABLES.stream(), java.util.stream.Stream.of("DECISION_PLANS")).toList();
    static final List<String> ACCESS_TABLES = List.of("HOUSEHOLD_USERS","ACCESS_AUDIT");
    static final List<String> ALL_TABLES = java.util.stream.Stream.concat(TABLES.stream(),ACCESS_TABLES.stream()).toList();
    public record TableData(List<String> columns, List<List<String>> rows) {}
    public record Archive(int version, String createdAt, Map<String, TableData> tables) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public WorkspaceBackupService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.json = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE)
    public Map<String, Long> counts() {
        com.householdfinance.access.AccessContext.requireCustodian();
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        return result;
    }
    private List<String> columns(String table) {
        return jdbc.query("SELECT * FROM " + table + " WHERE 1=0", rs -> {
            List<String> names = new ArrayList<>(); var metadata = rs.getMetaData();
            for (int i=1; i<=metadata.getColumnCount(); i++) {
                String name = metadata.getColumnName(i);
                // Internal migration row IDs are not referenced; let H2 generate fresh IDs after recovery.
                if (!(table.equals("MIGRATION_ARTIFACTS") && name.equals("ID"))) names.add(name);
            }
            return names;
        });
    }
    private void checkSchema() {
        Set<String> actual = new HashSet<>(jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_TYPE='BASE TABLE'", String.class));
        // Flyway owns its checksum/version ledger; recovery never imports schema history.
        actual.remove("flyway_schema_history");
        if (!actual.equals(new HashSet<>(ALL_TABLES))) throw new IllegalArgumentException("Database schema is not supported by this backup version");
    }
    // Hold admission through transaction completion, including direct domain-service callers.
    // HTTP mutations are additionally serialized by the existing WorkspaceFilter.
    private final ReentrantLock planWrites = new ReentrantLock(true);
    public void guardPlanMutation() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Plan admission requires a transaction");
        planWrites.lock();
        try {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { planWrites.unlock(); }
                });
        } catch (RuntimeException e) { planWrites.unlock(); throw e; }
    }
    private Archive snapshot() {
        checkSchema();
        Map<String, TableData> tables = new LinkedHashMap<>();
        int[] bytes = {0};
        for (String table : ALL_TABLES) {
            List<String> cols = columns(table);
            List<List<String>> rows = jdbc.query("SELECT " + String.join(",", cols) + " FROM " + table, rs -> {
                List<List<String>> result = new ArrayList<>();
                while (rs.next()) {
                    if (result.size() >= 50_000) throw new IllegalArgumentException("Too many records for local backup");
                    List<String> row = new ArrayList<>();
                    for (int i=1;i<=cols.size();i++) {
                        String value = rs.getString(i);
                        bytes[0] += value == null ? 4 : value.length() * 6 + 4;
                        if (bytes[0] > BackupCrypto.MAX_BYTES - 1024) throw new IllegalArgumentException("Workspace exceeds local backup limit");
                        row.add(value);
                    }
                    result.add(row);
                }
                return result;
            });
            tables.put(table, new TableData(cols, rows));
        }
        return new Archive(5, Instant.now().toString(), tables);
    }
    private byte[] encodeSnapshot() {
        byte[] plain;
        try { plain = json.writeValueAsBytes(snapshot()); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot encode backup", e); }
        if (plain.length > BackupCrypto.MAX_BYTES - 64) {
            Arrays.fill(plain, (byte)0);
            throw new IllegalArgumentException("Workspace exceeds the 10 MiB backup limit");
        }
        return plain;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void assertPlanMutationRecoverable() {
        // Same manifest, row bounds, conservative accounting and encoded-size limit as export.
        // Caller has flushed its pending mutation; a rejection rolls back that whole transaction.
        byte[] plain = encodeSnapshot();
        Arrays.fill(plain, (byte)0);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void assertMutationRecoverable() {
        // Flush *all* pending JPA writes into this same transaction before the
        // authoritative snapshot. A rejection rolls back every affected table.
        entityManager.flush();
        byte[] plain=encodeSnapshot();
        Arrays.fill(plain,(byte)0);
    }
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE)
    public byte[] exportBackup(String password) {
        com.householdfinance.access.AccessContext.requireCustodian();
        BackupCrypto.validatePassword(password);
        byte[] plain = encodeSnapshot();
        try { return BackupCrypto.encrypt(plain,password); }
        finally { Arrays.fill(plain,(byte)0); }
    }
    @Transactional(isolation=Isolation.SERIALIZABLE, rollbackFor=Exception.class)
    public Map<String, Long> restore(byte[] envelope, String password, String confirmation) {
        com.householdfinance.access.AccessContext.requireCustodian();
        if (!"RESTORE INTO EMPTY WORKSPACE".equals(confirmation)) throw new IllegalArgumentException("Explicit restore confirmation is required");
        checkSchema();
        if (counts().values().stream().anyMatch(n -> n != 0)) throw new IllegalArgumentException("Restore requires an empty workspace; existing records were kept");
        byte[] plain = BackupCrypto.decrypt(envelope,password);
        Archive archive;
        try { archive = json.readValue(plain,Archive.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid backup contents"); }
        finally { Arrays.fill(plain,(byte)0); }
        if (archive==null || archive.version()<1 || archive.version()>5 || archive.createdAt()==null || archive.tables()==null) throw new IllegalArgumentException("Incomplete or unsupported backup");
        List<String> restoreTables = archive.version()==1 ? V1_TABLES : archive.version()==2 ? V2_TABLES : archive.version()==3 ? V3_TABLES : archive.version()==4 ? TABLES : ALL_TABLES;
        if (!archive.tables().keySet().equals(new HashSet<>(restoreTables))) throw new IllegalArgumentException("Incomplete or unsupported backup");
        var currentOwner=jdbc.queryForList("SELECT CREDENTIAL FROM HOUSEHOLD_USERS WHERE ID='owner'");
        try { Instant.parse(archive.createdAt()); } catch (Exception e) { throw new IllegalArgumentException("Invalid backup timestamp"); }
        // Validate the entire manifest before the first insert. A later constraint error rolls back all tables.
        for (String table : restoreTables) {
            TableData data = archive.tables().get(table);
            var expected=columns(table);
            if(archive.version()<5)expected=expected.stream().filter(c -> !Set.of("OWNER_ID","SHARED","ACTOR_ID","SCOPE_REFS").contains(c)).toList();
            if (data==null || data.columns()==null || data.columns().size()!=expected.size()
                || !new HashSet<>(expected).equals(new HashSet<>(data.columns()))
                || data.rows()==null || data.rows().size()>50_000) throw new IllegalArgumentException("Backup schema mismatch");
            for (var row : data.rows()) {
                if (row==null || row.size()!=data.columns().size()) throw new IllegalArgumentException("Incomplete backup row");
                String amountColumn=switch(table) {
                    case "ACCOUNTS" -> "BALANCE";
                    case "COMMITMENTS", "MONTHLY_COMMITMENTS", "TRANSACTIONS" -> "AMOUNT";
                    default -> null;
                };
                if (amountColumn!=null) {
                    String value=row.get(data.columns().indexOf(amountColumn));
                    if (value==null) throw new IllegalArgumentException("Missing legacy amount in backup");
                    com.householdfinance.service.LegacyMoney.require(new java.math.BigDecimal(value),
                        table.equals("ACCOUNTS") || table.equals("TRANSACTIONS"));
                }
                if (table.equals("CATEGORIES"))
                    com.householdfinance.service.LegacyMoney.parse(row.get(data.columns().indexOf("BUDGET")),false);
            }
        }
        if(archive.version()==5){
            if(jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE ID<>'owner'",Long.class)>0)throw new IllegalArgumentException("Recovery needs a fresh installation without other users");
            var identities=archive.tables().get("HOUSEHOLD_USERS");int id=identities.columns().indexOf("ID"),state=identities.columns().indexOf("STATE"),roles=identities.columns().indexOf("ROLES");
            validateIdentities(archive, identities);
            if(!identities.rows().isEmpty()&&(identities.rows().stream().noneMatch(r -> "owner".equals(r.get(id)) && "ACTIVE".equals(r.get(state)))||identities.rows().stream().noneMatch(r -> "ACTIVE".equals(r.get(state)) && Arrays.asList(Objects.toString(r.get(roles),"").split(",")).contains("ADMIN"))))throw new IllegalArgumentException("Recovery must preserve the active recovery custodian and an administrator");
            if(identities.rows().isEmpty()){
                for(String table:List.of("LEDGER_ACCOUNTS","CASH_POSITION_SNAPSHOTS","DECISION_PLANS")){
                    var data=archive.tables().get(table);int owner=data.columns().indexOf("OWNER_ID");
                    if(data.rows().stream().anyMatch(r -> r.get(owner)!=null&&!r.get(owner).equals("owner")))throw new IllegalArgumentException("Backup has records without an owning identity");
                }
            }else {jdbc.update("DELETE FROM ACCESS_AUDIT");jdbc.update("DELETE FROM HOUSEHOLD_USERS");}
        }
        for (String table : restoreTables) {
            TableData data = archive.tables().get(table);
            // Column identities are validated against the fixed schema above.
            // Preserve archive order: ORM upgrades can reorder physical columns.
            List<String> cols = data.columns();
            String sql = "INSERT INTO " + table + " (" + String.join(",",cols) + ") VALUES (" + String.join(",",Collections.nCopies(cols.size(),"?")) + ")";
            for (List<String> row : data.rows()) jdbc.update(sql, statement -> {
                for (int i=0; i<row.size(); i++) statement.setString(i+1,row.get(i));
            });
        }
        // Old archives have no identity fields: only the original owner inherits them.
        if(archive.version()<5){
            for(String table:List.of("LEDGER_ACCOUNTS","CASH_POSITION_SNAPSHOTS","DECISION_PLANS"))jdbc.update("UPDATE "+table+" SET OWNER_ID='owner' WHERE OWNER_ID IS NULL");
            jdbc.update("UPDATE LEDGER_AUDIT SET ACTOR_ID='owner' WHERE ACTOR_ID IS NULL");
        }
        if(archive.version()==5&&!archive.tables().get("HOUSEHOLD_USERS").rows().isEmpty()){
            // An old archive must not silently reactivate former users, invitations or recovery codes.
            jdbc.update("UPDATE HOUSEHOLD_USERS SET STATE='DISABLED',INVITATION_HASH=NULL,INVITATION_EXPIRY=NULL,RECOVERY_HASH=NULL,REVISION=REVISION+1 WHERE ID<>'owner'");
            jdbc.update("UPDATE HOUSEHOLD_USERS SET STATE='ACTIVE',ROLES='ADMIN,EDITOR,READER',RECOVERY_HASH=NULL,INVITATION_HASH=NULL,INVITATION_EXPIRY=NULL,REVISION=REVISION+1 WHERE ID='owner'");
            if(!currentOwner.isEmpty())jdbc.update("UPDATE HOUSEHOLD_USERS SET CREDENTIAL=? WHERE ID='owner'",currentOwner.get(0).get("CREDENTIAL"));
            jdbc.update("INSERT INTO ACCESS_AUDIT(ID,ACTOR_ID,TARGET_ID,ACTION,OUTCOME,RECORDED_AT) VALUES(?,'owner','owner','RESTORE_ACCESS','REVIEW_USERS',?)",UUID.randomUUID().toString(),java.sql.Timestamp.from(Instant.now()));
        }
        return counts();
    }

    private void validateIdentities(Archive archive, TableData identities) {
        Set<String> ids=new HashSet<>(), names=new HashSet<>();
        var cols=identities.columns();
        for(var row:identities.rows()) {
            String id=row.get(cols.indexOf("ID")), name=row.get(cols.indexOf("USERNAME"));
            String state=row.get(cols.indexOf("STATE")), roles=row.get(cols.indexOf("ROLES"));
            String credential=row.get(cols.indexOf("CREDENTIAL"));
            if(id==null || !(id.equals("owner") || id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) || !ids.add(id)
                    || name==null || !name.matches("[a-z][a-z0-9_.-]{2,39}") || !names.add(name)
                    || !Set.of("ACTIVE","DISABLED","INVITED").contains(Objects.toString(state,""))
                    || roles==null || !Arrays.stream(roles.isEmpty()?new String[0]:roles.split(",",-1)).allMatch(com.householdfinance.access.AccessService.ROLES::contains)
                    || ("ACTIVE".equals(state) && credential==null)
                    || (credential!=null && !credential.matches("[A-Za-z0-9+/]{22}==:[A-Za-z0-9+/]{43}=")))
                throw new IllegalArgumentException("Invalid recovery identity");
            try { if(Long.parseLong(row.get(cols.indexOf("REVISION")))<0)throw new NumberFormatException(); }
            catch(RuntimeException e){throw new IllegalArgumentException("Invalid recovery identity revision");}
            if(id.equals("owner") && !name.equals("owner"))throw new IllegalArgumentException("Invalid recovery custodian");
        }
        if(ids.isEmpty())ids.add("owner"); // Older direct-service archives contained no login catalogue.
        for(String table:List.of("LEDGER_ACCOUNTS","CASH_POSITION_SNAPSHOTS","DECISION_PLANS")) {
            var data=archive.tables().get(table); int owner=data.columns().indexOf("OWNER_ID");
            for(var row:data.rows())if(!ids.contains(Objects.toString(row.get(owner),"owner")))
                throw new IllegalArgumentException("Backup has records without an owning identity");
        }
    }
}
