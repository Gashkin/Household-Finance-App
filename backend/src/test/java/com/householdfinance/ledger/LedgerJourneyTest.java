package com.householdfinance.ledger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.workspace.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "workspace.password=synthetic-ledger-workspace-password",
      "spring.datasource.url=jdbc:h2:mem:ledger-journey;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class LedgerJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired LedgerService service;
  @Autowired LedgerAuditRepository audits;
  @Autowired ActivityReviewService review;
  @Autowired JdbcTemplate jdbc;
  @Autowired WorkspaceBackupService backups;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  Cookie session;
  LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));

  @BeforeEach
  void reset() throws Exception {
    var tables = new ArrayList<>(backups.counts().keySet());
    Collections.reverse(tables);
    for (String table : tables) jdbc.update("DELETE FROM " + table);
    var response =
        mvc.perform(
                post("/api/workspace/unlock")
                    .header("X-Workspace-Request", "1")
                    .contentType("application/json")
                    .content("{\"password\":\"synthetic-ledger-workspace-password\"}"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    session =
        new Cookie(
            "workspace_session",
            response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]);
  }

  MockHttpServletRequestBuilder request(String path, Object value) throws Exception {
    return post("/api/ledger" + path)
        .cookie(session)
        .header("X-Workspace-Request", "1")
        .contentType("application/json")
        .content(json.writeValueAsBytes(value));
  }

  JsonNode postOk(String path, Object value) throws Exception {
    return json.readTree(
        mvc.perform(request(path, value))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  LedgerService.AccountView account(String kind, String opening) {
    return service.create(
        new LedgerService.AccountInput(
            "Synthetic " + kind + UUID.randomUUID().toString().substring(0, 4),
            kind,
            today.minusDays(3),
            opening,
            "Synthetic opening statement"));
  }

  @Test
  void positiveDebtStatementCreditIncreasesNetButNeverSpendableCash() {
    var credit = account("DEBT", "25.00");
    var position = service.position(today);
    assertEquals(
        2500,
        position.accounts().stream()
            .filter(a -> a.id().equals(credit.id()))
            .findFirst()
            .orElseThrow()
            .bookedMinor());
    assertEquals(0, position.cashMinor());
    assertEquals(2500, position.netMinor());
  }

  @Test
  void unusedAccountCanBeRemovedWithMinimalTombstoneAndActivityBlocksRemoval() {
    var createdUnused = account("CASH", "25.00");
    var unused = service.correctOpening(createdUnused.id(), new LedgerService.OpeningCorrection(
        createdUnused.revision(), today.minusDays(2), "30.00", "Synthetic corrected opening"));
    service.removeUnusedAccount(unused.id(),
        new LedgerService.RemoveUnusedAccount(unused.revision(), "Synthetic duplicate account"));
    assertTrue(service.position(today).accounts().stream().noneMatch(a -> a.id().equals(unused.id())));
    var removalHistory = audits.findByAccountIdOrderByRecordedAtDesc(unused.id());
    assertTrue(removalHistory.stream()
        .anyMatch(e -> e.action.equals("ACCOUNT_REMOVED") && e.source.equals("Synthetic duplicate account")));
    assertTrue(removalHistory.stream().anyMatch(e -> e.action.equals("OPEN")));
    assertTrue(removalHistory.stream().anyMatch(e -> e.action.equals("OPENING_CORRECTION")));

    var used = account("CASH", "25.00");
    importRows(used, row("keep-history", "-1.00", "BOOKED", "ACTIVITY"));
    var current = current(used.id());
    assertThrows(IllegalArgumentException.class, () -> service.removeUnusedAccount(used.id(),
        new LedgerService.RemoveUnusedAccount(current.revision(), "Synthetic attempted deletion")));
    assertTrue(service.position(today).accounts().stream().anyMatch(a -> a.id().equals(used.id())));
  }

  @Test
  void openingBalanceCanBeCorrectedOnlyBeforeActivityAndKeepsAudit() {
    var a = account("CASH", "1000.00");
    var corrected = service.correctOpening(a.id(), new LedgerService.OpeningCorrection(
        a.revision(), today.minusDays(1), "276.05", "Synthetic current AIB balance"));
    assertEquals(27605, corrected.openingMinor());
    assertEquals(today.minusDays(1), corrected.openingDate());
    assertTrue(service.history(a.id()).stream().anyMatch(e -> e.action.equals("OPENING_CORRECTION")));
    importRows(corrected, row("one-activity", "-1.00", "BOOKED", "ACTIVITY"));
    var current = current(a.id());
    assertThrows(IllegalArgumentException.class, () -> service.correctOpening(a.id(),
        new LedgerService.OpeningCorrection(current.revision(), today, "100.00", "Synthetic late correction")));
    assertEquals(27505, current(a.id()).bookedMinor());
  }

  @Test
  void standardAibDebitCreditExportPreviewsSignsAndLatestBalance() {
    var a = account("CASH", "100.00");
    String aib = "Posted Date,Description,Debit Amount,Credit Amount,Balance,Transaction Type\n"
        + today.minusDays(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"))
        + ",Synthetic groceries,12.34,,87.66,Debit\n"
        + today.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"))
        + ",Synthetic refund,,5.00,92.66,Credit\n";
    var preview = service.preview(a.id(), new LedgerService.PreviewInput(aib, "Synthetic AIB CSV"));
    assertEquals("AIB", preview.format());
    assertEquals(today.minusDays(1), preview.statementDate());
    assertEquals("92.66", preview.statementBalanceEuro());
    assertEquals(List.of("-12.34", "5.00"), preview.proposals().stream()
        .map(p -> p.row().amountEuro()).toList());
    var approved = service.approve(a.id(), preview.id(), new LedgerService.Approval(
        preview.revision(), preview.proposals().stream().map(LedgerService.Proposal::row).toList(), true));
    assertEquals(2, approved.added());
    assertEquals(9266, current(a.id()).bookedMinor());
    var repeated = service.preview(a.id(), new LedgerService.PreviewInput(aib, "Synthetic AIB repeat"));
    assertTrue(repeated.proposals().stream().allMatch(p -> p.disposition().equals("DUPLICATE")));
  }

  @Test
  void standardAibAmountTypeAndGroupedSemicolonHeadersAreRecognised() {
    String date = today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"));
    var simple = LedgerCsv.parseStatement(
        "Posted Date,Description,Amount,Transaction Type,Balance\n"
            + date + ",Synthetic card purchase,7.25,Debit,15.75\n");
    assertEquals("-7.25", simple.rows().getFirst().get(3));
    assertEquals("15.75", simple.statementBalanceEuro());

    var grouped = LedgerCsv.parseStatement(
        "Posted;Account;Posted;Transactions;Date;Description;Debit;Amount;Credit;Amount;Balance;Transaction;Type\n"
            + "Posted;masked;Posted;Transactions;" + date + ";Synthetic debit;;7.25;;;15.75;Transaction;Debit\n");
    assertEquals("-7.25", grouped.rows().getFirst().get(3));
    assertEquals("AIB", grouped.format());
  }

  LedgerService.Row row(String reference, String amount, String state, String kind) {
    return new LedgerService.Row(reference, today, "Synthetic " + reference, amount, state, kind);
  }

  String csv(LedgerService.Row... rows) {
    StringBuilder s = new StringBuilder("reference,date,description,amount,status,kind\n");
    for (var r : rows)
      s.append(r.reference())
          .append(',')
          .append(r.date())
          .append(',')
          .append(r.description())
          .append(',')
          .append(r.amountEuro())
          .append(',')
          .append(r.status())
          .append(',')
          .append(r.kind())
          .append('\n');
    return s.toString();
  }

  LedgerService.Imported importRows(LedgerService.AccountView a, LedgerService.Row... rows) {
    var p = service.preview(a.id(), new LedgerService.PreviewInput(csv(rows), "Synthetic CSV"));
    return service.approve(
        a.id(),
        p.id(),
        new LedgerService.Approval(
            p.revision(), p.proposals().stream().map(LedgerService.Proposal::row).toList(), true));
  }

  LedgerService.AccountView current(String id) {
    return service.position(today).accounts().stream()
        .filter(a -> a.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  void reconcile(String id, String statement) {
    var a = current(id);
    service.reconcile(
        id,
        today,
        new LedgerService.Reconcile(
            a.revision(), statement, "Synthetic closing statement", true, false));
  }

  LedgerService.Publish publication(String reserve, List<LedgerService.Commitment> commitments) {
    var p = service.position(today);
    Map<String, Long> revisions = new TreeMap<>();
    p.accounts().forEach(a -> revisions.put(a.id(), a.revision()));
    return new LedgerService.Publish(
        UUID.randomUUID().toString(), today, revisions, reserve, commitments, true);
  }

  @Test
  void reviewedImportReconcilesAndFeedsDebtReportWithoutPendingOrTransferDoubleCount()
      throws Exception {
    var a = account("CASH", "1000");
    var preview =
        postOk(
            "/accounts/" + a.id() + "/preview",
            Map.of(
                "csv",
                csv(
                    row("income", "1000", "BOOKED", "ACTIVITY"),
                    row("groceries", "-100", "BOOKED", "ACTIVITY"),
                    row("card-payment", "-200", "BOOKED", "TRANSFER"),
                    row("pending-debit", "-50", "PENDING", "ACTIVITY"),
                    row("pending-credit", "20", "PENDING", "ACTIVITY")),
                "source",
                "Synthetic statement CSV"));
    var selected = json.createArrayNode();
    preview.get("proposals").forEach(p -> selected.add(p.get("row")));
    var approval = Map.of("revision", 0, "rows", selected, "acknowledgedOverlap", true);
    String path = "/accounts/" + a.id() + "/previews/" + preview.get("id").asText() + "/approve";
    assertEquals(5, postOk(path, approval).get("added").asInt());
    assertEquals(5, postOk(path, approval).get("added").asInt());
    assertEquals(5, service.activity(a.id()).size());
    var debt = account("DEBT", "-500");
    importRows(
        debt,
        row("card-buy", "-40", "BOOKED", "ACTIVITY"),
        row("card-repay", "200", "BOOKED", "TRANSFER"));
    var position = service.position(today);
    assertEquals(170000, position.cashMinor());
    assertEquals(-34000, position.debtSignedMinor());
    assertEquals(136000, position.netMinor());
    assertEquals(100000, position.incomeMinor());
    assertEquals(14000, position.spendingMinor());
    assertEquals(5000, position.cashPendingDebitMinor());
    var input = publication("400", List.of(new LedgerService.Commitment("Unpaid rent", "100")));
    mvc.perform(request("/publish", input)).andExpect(status().isBadRequest());
    reconcile(a.id(), "1700");
    input = publication("400", List.of(new LedgerService.Commitment("Unpaid rent", "100")));
    var snapshot = postOk("/publish", input);
    var repeat = postOk("/publish", input);
    assertEquals(snapshot.get("id"), repeat.get("id"));
    assertEquals(170000, snapshot.get("liquidCashMinor").asLong());
    assertEquals(15000, snapshot.get("committedMinor").asLong());
    assertEquals(155000, snapshot.get("availableAfterCommitmentsMinor").asLong());
    mvc.perform(
            post("/api/debt-scenarios/report")
                .cookie(session)
                .header("X-Workspace-Request", "1")
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "balanceMinor",
                            100000,
                            "annualRatePercent",
                            "12",
                            "monthlyPaymentMinor",
                            10000,
                            "extraPaymentMinor",
                            20000,
                            "liquidCashMinor",
                            155000,
                            "minimumReserveMinor",
                            40000,
                            "cashPositionSnapshotId",
                            snapshot.get("id").asText()))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.cashEvidence.sourceNote")
                .value(org.hamcrest.Matchers.containsString("Reviewed ledger")));
    assertTrue(
        service.history("HOUSEHOLD").stream()
            .anyMatch(
                e ->
                    e.action.equals("PUBLISH_EVIDENCE")
                        && e.afterJson.contains("Synthetic closing statement")));
  }

  @Test
  void sameReferenceSettlesPendingOnceAndReimportSkipsBookedDuplicates() {
    var a = account("CASH", "1000");
    importRows(a, row("stable-bank-id", "-50", "PENDING", "ACTIVITY"));
    var p =
        service.preview(
            a.id(),
            new LedgerService.PreviewInput(
                csv(row("stable-bank-id", "-55", "BOOKED", "ACTIVITY")), "Synthetic booked feed"));
    assertEquals("SETTLE_PENDING", p.proposals().get(0).disposition());
    var result =
        service.approve(
            a.id(),
            p.id(),
            new LedgerService.Approval(
                p.revision(),
                p.proposals().stream().map(LedgerService.Proposal::row).toList(),
                true));
    assertEquals(1, result.settled());
    assertEquals(1, service.activity(a.id()).size());
    assertEquals(94500, current(a.id()).bookedMinor());
    assertEquals(0, current(a.id()).pendingDebitMinor());
    p =
        service.preview(
            a.id(),
            new LedgerService.PreviewInput(
                csv(row("stable-bank-id", "-55", "BOOKED", "ACTIVITY")), "Repeated CSV"));
    assertEquals("DUPLICATE", p.proposals().get(0).disposition());
    assertTrue(
        service.history(a.id()).stream()
            .anyMatch(
                e ->
                    e.action.equals("SETTLE_PENDING")
                        && e.beforeJson.contains("-50.00")
                        && e.afterJson.contains("-55.00")));
  }

  @Test
  void conflictingBatchRollsBackEveryNewRowAndOverlapNeedsReview() {
    var a = account("CASH", "1000");
    importRows(a, row("saved", "-20", "BOOKED", "ACTIVITY"));
    var before = backups.counts();
    var p =
        service.preview(
            a.id(),
            new LedgerService.PreviewInput(
                csv(
                    row("new", "-5", "BOOKED", "ACTIVITY"),
                    row("saved", "-25", "BOOKED", "ACTIVITY")),
                "Conflicting CSV"));
    var rows = p.proposals().stream().map(LedgerService.Proposal::row).toList();
    var key = p.id();
    var rev = p.revision();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.approve(a.id(), key, new LedgerService.Approval(rev, rows, true)));
    assertEquals(before, backups.counts());
    p =
        service.preview(
            a.id(),
            new LedgerService.PreviewInput(
                csv(row("other-id", "-20", "BOOKED", "ACTIVITY")), "Possible same manual entry"));
    assertTrue(p.proposals().get(0).possibleOverlap());
    var p2 = p;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.approve(
                a.id(),
                p2.id(),
                new LedgerService.Approval(
                    p2.revision(), List.of(row("other-id", "-20", "BOOKED", "ACTIVITY")), false)));
    assertEquals(1, service.activity(a.id()).size());
  }

  @Test
  void explicitCorrectionsVoidDuplicatesAndInvalidateReconciliation() throws Exception {
    var a = account("CASH", "1000");
    importRows(a, row("correct-me", "-20", "BOOKED", "ACTIVITY"));
    reconcile(a.id(), "980");
    var entry = service.activity(a.id()).get(0);
    review.reopen(a.id(),new ActivityReviewService.Reopen(current(a.id()).revision(),"Synthetic receipt correction requires reopening"));
    long revision = current(a.id()).revision();
    var correction =
        new LedgerService.Correction(
            revision,
            row("correct-me", "-25", "BOOKED", "ACTIVITY"),
            "Synthetic source corrected amount");
    mvc.perform(
            put("/api/ledger/accounts/" + a.id() + "/activity/" + entry.id)
                .cookie(session)
                .header("X-Workspace-Request", "1")
                .contentType("application/json")
                .content(json.writeValueAsBytes(correction)))
        .andExpect(status().isOk());
    assertEquals(97500, current(a.id()).bookedMinor());
    assertFalse(current(a.id()).reconciled());
    assertTrue(
        service.history(a.id()).stream()
            .anyMatch(
                e ->
                    e.action.equals("CORRECTION")
                        && e.beforeJson.contains("-20.00")
                        && e.afterJson.contains("-25.00")));
    service.correct(
        a.id(),
        entry.id,
        new LedgerService.Correction(
            current(a.id()).revision(),
            row("correct-me", "-25", "VOID", "ACTIVITY"),
            "Cancelled duplicate"));
    assertEquals(100000, current(a.id()).bookedMinor());
    assertEquals(0, current(a.id()).spendingMinor());
    assertEquals("VOID", service.activity(a.id()).get(0).status);
    assertThrows(
        IllegalArgumentException.class, () -> service.correct(a.id(), entry.id, correction));
  }

  @Test
  void mismatchNeedsExplicitAdjustmentAndDoesNotInventSpending() {
    var a = account("CASH", "1000");
    importRows(a, row("purchase", "-50", "BOOKED", "ACTIVITY"));
    long revision = current(a.id()).revision();
    var preview =
        service.reconcile(
            a.id(),
            today,
            new LedgerService.Reconcile(
                revision, "940", "Synthetic closing statement", false, false));
    assertEquals(-1000, preview.differenceMinor());
    assertEquals(1, service.activity(a.id()).size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.reconcile(
                a.id(),
                today,
                new LedgerService.Reconcile(
                    revision, "940", "Synthetic closing statement", true, false)));
    service.reconcile(
        a.id(),
        today,
        new LedgerService.Reconcile(revision, "940", "Synthetic closing statement", true, true));
    assertEquals(94000, current(a.id()).bookedMinor());
    assertTrue(current(a.id()).reconciled());
    assertEquals(5000, current(a.id()).spendingMinor());
    assertEquals(
        1, service.activity(a.id()).stream().filter(e -> e.kind.equals("ADJUSTMENT")).count());
    assertFalse(service.position(today.minusDays(1)).accounts().get(0).reconciled());
  }

  @Test
  void overdraftsReduceCashWhileDebtAndCreditHeadroomNeverBecomeSpendableCash() {
    var overdrawn = account("CASH", "-50");
    var savings = account("CASH", "100");
    account("DEBT", "-10000");
    reconcile(overdrawn.id(), "-50");
    reconcile(savings.id(), "100");
    var snapshot = service.publish(publication("0", List.of()));
    assertEquals(5000, snapshot.liquidCashMinor());
    assertEquals(
        -5000,
        snapshot.cashBalances().stream()
            .filter(l -> l.label().equals(overdrawn.name()))
            .findFirst()
            .orElseThrow()
            .amountMinor());
    assertEquals(-995000, service.position(today).netMinor());
  }

  @Test
  void malformedTypesDatesAmountsAndStaleRevisionsCannotMutate() throws Exception {
    mvc.perform(get("/api/ledger?asOf=" + today)).andExpect(status().isUnauthorized());
    for (Object amount : List.of("1.001", "1e2", "NaN", "1,000.00", 12.34))
      mvc.perform(
              request(
                  "/accounts",
                  Map.of(
                      "name",
                      "Test",
                      "kind",
                      "CASH",
                      "openingDate",
                      today.toString(),
                      "openingEuro",
                      amount,
                      "source",
                      "Synthetic")))
          .andExpect(status().isBadRequest());
    var a = account("CASH", "100");
    var r = row("one", "-1", "BOOKED", "ACTIVITY");
    for (Object rev : List.of(0.5, "0"))
      mvc.perform(
              request(
                  "/accounts/" + a.id() + "/activity",
                  Map.of(
                      "revision",
                      rev,
                      "row",
                      r,
                      "source",
                      "Synthetic",
                      "acknowledgedOverlap",
                      true)))
          .andExpect(status().isBadRequest());
    mvc.perform(
            request(
                "/accounts/" + a.id() + "/activity",
                Map.of(
                    "revision", 0, "row", r, "source", "Synthetic", "acknowledgedOverlap", "true")))
        .andExpect(status().isBadRequest());
    assertTrue(service.activity(a.id()).isEmpty());
    var preview = service.preview(a.id(), new LedgerService.PreviewInput(csv(r), "Synthetic CSV"));
    service.manual(
        a.id(),
        new LedgerService.Manual(0L, row("two", "2", "BOOKED", "ACTIVITY"), "Synthetic", true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.approve(
                a.id(), preview.id(), new LedgerService.Approval(0L, List.of(r), true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.manual(
                a.id(),
                new LedgerService.Manual(
                    1L,
                    new LedgerService.Row(
                        "future", today.plusDays(1), "Future", "1", "BOOKED", "ACTIVITY"),
                    "Synthetic",
                    true)));
  }

  @Test
  void ledgerAuditAndPublishedEvidenceSurviveEncryptedRecoveryAndV1StillRestores()
      throws Exception {
    var a = account("CASH", "100");
    importRows(a, row("p", "-5", "BOOKED", "ACTIVITY"));
    reconcile(a.id(), "95");
    var snapshot = service.publish(publication("0", List.of()));
    var before = backups.counts();
    byte[] encrypted = backups.exportBackup("synthetic-backup-passphrase-1234");
    var archive =
        json.readValue(
            BackupCrypto.decrypt(encrypted, "synthetic-backup-passphrase-1234"),
            WorkspaceBackupService.Archive.class);
    assertEquals(7, archive.version());
    var tables = new ArrayList<>(before.keySet());
    Collections.reverse(tables);
    for (String table : tables) jdbc.update("DELETE FROM " + table);
    assertEquals(
        before,
        backups.restore(
            encrypted, "synthetic-backup-passphrase-1234", "RESTORE INTO EMPTY WORKSPACE"));
    assertEquals(9500, current(a.id()).bookedMinor());
    assertTrue(current(a.id()).reconciled());
    assertTrue(
        service.history("HOUSEHOLD").stream()
            .anyMatch(e -> e.source.equals("Snapshot " + snapshot.id())));
    var old = new LinkedHashMap<>(com.householdfinance.workspace.LegacyArchiveFixture.tables(archive,1));
    old.keySet().removeIf(k -> k.startsWith("LEDGER_") || k.equals("FORECAST_PLANS") || k.equals("DECISION_PLANS"));
    byte[] v1 =
        BackupCrypto.encrypt(
            json.writeValueAsBytes(new WorkspaceBackupService.Archive(1, archive.createdAt(), old)),
            "synthetic-backup-passphrase-1234");
    for (String table : tables) jdbc.update("DELETE FROM " + table);
    backups.restore(v1, "synthetic-backup-passphrase-1234", "RESTORE INTO EMPTY WORKSPACE");
    assertTrue(service.position(today).accounts().isEmpty());
    assertEquals(1L, backups.counts().get("CASH_POSITION_SNAPSHOTS"));
  }

  @Test
  void approvalRetryDoesNotCacheARolledBackTransaction() {
    var a = account("CASH", "100");
    var preview =
        service.preview(
            a.id(),
            new LedgerService.PreviewInput(
                csv(row("rollback", "-5", "BOOKED", "ACTIVITY")), "Synthetic rollback source"));
    var approval =
        new LedgerService.Approval(
            preview.revision(),
            preview.proposals().stream().map(LedgerService.Proposal::row).toList(),
            true);
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .execute(
            status -> {
              service.approve(a.id(), preview.id(), approval);
              status.setRollbackOnly();
              return null;
            });
    assertTrue(service.activity(a.id()).isEmpty());
    service.approve(a.id(), preview.id(), approval);
    assertEquals(1, service.activity(a.id()).size());
    assertEquals(9500, current(a.id()).bookedMinor());
  }

  @Test
  void quotedCsvAndBoundedParserRejectAmbiguity() {
    assertEquals(
        "Coffee, shop",
        LedgerCsv.parse(
                "\uFEFFreference,date,description,amount,status,kind\r\na,"
                    + today
                    + ",\"Coffee, shop\",-2.50,BOOKED,ACTIVITY\r\n")
            .get(0)
            .get(2));
    for (String csv :
        List.of(
            "wrong,header",
            "reference,date,description,amount,status,kind\na,b,\"unclosed",
            "reference,date,description,amount,status,kind\na,b,\"quoted\"junk,1,BOOKED,ACTIVITY",
            "x".repeat(100001)))
      assertThrows(IllegalArgumentException.class, () -> LedgerCsv.parse(csv));
  }
}
