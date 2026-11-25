package com.householdfinance.contracts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.householdfinance.contracts.ContractReview.Record;
import static com.householdfinance.contracts.ContractReview.*;

@Repository
class ContractStore {
  record Saved(String id, String owner, boolean shared, long revision, Record record) {}
  record Alert(String contract, String term, String timing, Disposition disposition, LocalDate until) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  ContractStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  List<Saved> visible(String user) {
    return jdbc.query("SELECT * FROM HOUSEHOLD_CONTRACTS WHERE OWNER_ID=? OR SHARED", this::contract, user);
  }

  Saved visible(String id, String user) {
    return jdbc.query("SELECT * FROM HOUSEHOLD_CONTRACTS WHERE ID=? AND (OWNER_ID=? OR SHARED)",
        this::contract, id, user).stream().findFirst().orElse(null);
  }

  List<Saved> all() {
    return jdbc.query("SELECT * FROM HOUSEHOLD_CONTRACTS", this::contract);
  }

  long count(String owner) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_CONTRACTS WHERE OWNER_ID=?", Long.class, owner);
  }

  void insert(String id, String owner, Record record) {
    jdbc.update("INSERT INTO HOUSEHOLD_CONTRACTS VALUES(?,?,FALSE,1,?)", id, owner, encode(record));
  }

  void replace(Saved saved, boolean shared, Record record) {
    if (jdbc.update("""
        UPDATE HOUSEHOLD_CONTRACTS SET SHARED=?, REVISION=REVISION+1, PAYLOAD=?
        WHERE ID=? AND REVISION=?
        """, shared, encode(record), saved.id(), saved.revision()) != 1) {
      throw new IllegalArgumentException("Contract changed. Reload before saving.");
    }
  }

  List<String> readers() {
    return jdbc.queryForList("""
        SELECT ID FROM HOUSEHOLD_USERS WHERE STATE='ACTIVE'
        AND ((',' || ROLES || ',') LIKE '%,EDITOR,%' OR (',' || ROLES || ',') LIKE '%,READER,%')
        """, String.class);
  }

  List<Alert> alerts(String user) {
    return jdbc.query("SELECT * FROM CONTRACT_REVIEW_ALERTS WHERE RECIPIENT_ID=?", this::alert, user);
  }

  Alert alert(String contract, String term, String user) {
    return jdbc.query("SELECT * FROM CONTRACT_REVIEW_ALERTS WHERE CONTRACT_ID=? AND TERM_ID=? AND RECIPIENT_ID=?",
        this::alert, contract, term, user).stream().findFirst().orElse(null);
  }

  void saveAlert(String contract, String term, String user, String timing, Disposition disposition, LocalDate until) {
    jdbc.update("MERGE INTO CONTRACT_REVIEW_ALERTS KEY(CONTRACT_ID, TERM_ID, RECIPIENT_ID) VALUES(?,?,?,?,?,?)",
        contract, term, user, timing, disposition.name(), until);
  }

  void clearAlerts(String contract, String term) {
    jdbc.update("DELETE FROM CONTRACT_REVIEW_ALERTS WHERE CONTRACT_ID=? AND TERM_ID=?", contract, term);
  }

  void revokeAlerts() {
    jdbc.update("""
        DELETE FROM CONTRACT_REVIEW_ALERTS a WHERE NOT EXISTS (
          SELECT 1 FROM HOUSEHOLD_CONTRACTS c JOIN HOUSEHOLD_USERS u ON (c.SHARED OR c.OWNER_ID=u.ID)
          WHERE c.ID=a.CONTRACT_ID AND u.ID=a.RECIPIENT_ID AND u.STATE='ACTIVE'
            AND ((',' || u.ROLES || ',') LIKE '%,EDITOR,%' OR (',' || u.ROLES || ',') LIKE '%,READER,%')
        )
        """);
  }

  private Saved contract(ResultSet row, int index) throws SQLException {
    try {
      return new Saved(row.getString("ID"), row.getString("OWNER_ID"), row.getBoolean("SHARED"),
          row.getLong("REVISION"), json.readValue(row.getString("PAYLOAD"), Record.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored contract cannot be read", e);
    }
  }

  private Alert alert(ResultSet row, int index) throws SQLException {
    return new Alert(row.getString("CONTRACT_ID"), row.getString("TERM_ID"), row.getString("TIMING"),
        Disposition.valueOf(row.getString("DISPOSITION")), row.getObject("SNOOZED_UNTIL", LocalDate.class));
  }

  private String encode(Record record) {
    try { return json.writeValueAsString(record); }
    catch (JsonProcessingException e) { throw new IllegalStateException("Contract cannot be saved", e); }
  }
}
