package com.householdfinance.workspace;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceBackupService {
    // Fixed dependency order, controlled by code. Names in a backup never become SQL identifiers.
    static final List<String> TABLES = List.of("ACCOUNTS", "CATEGORIES", "COMMITMENTS", "MONTHLY_COMMITMENTS", "TRANSACTIONS", "MIGRATION_ARTIFACTS", "CASH_POSITION_SNAPSHOTS", "CASH_POSITION_LINES");
    public record TableData(List<String> columns, List<List<String>> rows) {}
    public record Archive(int version, String createdAt, Map<String, TableData> tables) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public WorkspaceBackupService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.json = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE)
    public Map<String, Long> counts() {
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
        if (!actual.equals(new HashSet<>(TABLES))) throw new IllegalArgumentException("Database schema is not supported by this backup version");
    }
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE)
    public byte[] exportBackup(String password) {
        BackupCrypto.validatePassword(password); checkSchema();
        Map<String, TableData> tables = new LinkedHashMap<>();
        int[] bytes = {0};
        for (String table : TABLES) {
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
        byte[] plain = null;
        try { plain = json.writeValueAsBytes(new Archive(1, Instant.now().toString(), tables)); return BackupCrypto.encrypt(plain,password); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot encode backup", e); }
        finally { if (plain != null) Arrays.fill(plain,(byte)0); }
    }
    @Transactional(isolation=Isolation.SERIALIZABLE, rollbackFor=Exception.class)
    public Map<String, Long> restore(byte[] envelope, String password, String confirmation) {
        if (!"RESTORE INTO EMPTY WORKSPACE".equals(confirmation)) throw new IllegalArgumentException("Explicit restore confirmation is required");
        checkSchema();
        if (counts().values().stream().anyMatch(n -> n != 0)) throw new IllegalArgumentException("Restore requires an empty workspace; existing records were kept");
        byte[] plain = BackupCrypto.decrypt(envelope,password);
        Archive archive;
        try { archive = json.readValue(plain,Archive.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid backup contents"); }
        finally { Arrays.fill(plain,(byte)0); }
        if (archive==null || archive.version()!=1 || archive.createdAt()==null || archive.tables()==null || !archive.tables().keySet().equals(new HashSet<>(TABLES))) throw new IllegalArgumentException("Incomplete or unsupported backup");
        try { Instant.parse(archive.createdAt()); } catch (Exception e) { throw new IllegalArgumentException("Invalid backup timestamp"); }
        // Validate the entire manifest before the first insert. A later constraint error rolls back all tables.
        for (String table : TABLES) {
            TableData data = archive.tables().get(table);
            if (data==null || !columns(table).equals(data.columns()) || data.rows()==null || data.rows().size()>50_000) throw new IllegalArgumentException("Backup schema mismatch");
            for (var row : data.rows()) if (row==null || row.size()!=data.columns().size()) throw new IllegalArgumentException("Incomplete backup row");
        }
        for (String table : TABLES) {
            TableData data = archive.tables().get(table);
            List<String> cols = columns(table);
            String sql = "INSERT INTO " + table + " (" + String.join(",",cols) + ") VALUES (" + String.join(",",Collections.nCopies(cols.size(),"?")) + ")";
            for (List<String> row : data.rows()) jdbc.update(sql, statement -> {
                for (int i=0; i<row.size(); i++) statement.setString(i+1,row.get(i));
            });
        }
        return counts();
    }
}
