package com.householdfinance;

import static org.junit.jupiter.api.Assertions.*;
import com.householdfinance.entity.*;
import com.householdfinance.repository.*;
import com.householdfinance.service.TransactionImportService;
import com.householdfinance.workspace.WorkspaceBackupService;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:legacy-import-security;DB_CLOSE_DELAY=-1"})
class TransactionImportSecurityTest {
  @Autowired TransactionImportService imports;
  @Autowired AccountRepository accounts;
  @Autowired TransactionRepository transactions;
  @Autowired WorkspaceBackupService backups;
  @Autowired JdbcTemplate jdbc;
  String csv="date,description,amount,account\n2026-11-01,Synthetic expense,-5.00,Synthetic checking";

  @BeforeEach void prepare() {
    var tables=new ArrayList<>(backups.counts().keySet());Collections.reverse(tables);
    tables.forEach(t->jdbc.update("DELETE FROM "+t));
    accounts.save(new AccountEntity("synthetic","Synthetic checking","Current Account",100));
    transactions.save(new TransactionEntity("future",LocalDate.of(2026,11,1),"Synthetic expense",-5,"Synthetic",TransactionType.ONE_OFF_FUTURE,null));
  }

  @Test void repeatedSelectionAndAcceptanceCannotCreateTwoBalanceAdjustments() {
    var preview=imports.previewCsv(csv);
    assertEquals(1,imports.acceptImport(new TransactionImportService.ImportAcceptRequest(preview.importId(),List.of("R1","R1"))).acceptedCount());
    assertEquals(0,imports.acceptImport(new TransactionImportService.ImportAcceptRequest(preview.importId(),List.of("R1"))).acceptedCount());
    var history=imports.getHistory().stream().filter(h->h.importId().equals(preview.importId())).toList();
    assertEquals(1,history.size());
    var resolution=new TransactionImportService.MapToFutureRequest(history.getFirst().historyId(),"future");
    imports.mapToFuture(resolution);
    assertThrows(IllegalArgumentException.class,()->imports.mapToFuture(resolution));
    assertEquals(95,accounts.findById("synthetic").orElseThrow().getBalance());
  }

  @Test void simultaneousAcceptanceIsIdempotent() throws Exception {
    var preview=imports.previewCsv(csv);
    var request=new TransactionImportService.ImportAcceptRequest(preview.importId(),List.of("R1"));
    var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> accept=()->{barrier.await();return imports.acceptImport(request).acceptedCount();};
      var a=pool.submit(accept);var b=pool.submit(accept);
      assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
    } finally { pool.shutdownNow(); }
  }

  @Test void malformedAndNonFiniteAmountsNeverBecomeZeroValueTransactions() {
    for(String amount:List.of("oops","NaN","Infinity","-Infinity","0.001")) {
      assertThrows(IllegalArgumentException.class,()->imports.previewCsv(csv.replace("-5.00",amount)),amount);
    }
  }

  @Test void quotedDescriptionsAreParsedWithoutChangingAccountOrAmount() {
    var preview=imports.previewCsv(csv.replace("Synthetic expense","\"Synthetic, expense\""));
    var row=preview.transactions().getFirst();
    assertEquals("Synthetic, expense",row.description());
    assertEquals(-5,row.amount());
    assertEquals("Synthetic checking",row.account());
  }

  @Test void failedResolutionRollsBackTheCreatedCommitmentAndKeepsHistoryUnresolved() {
    var preview=imports.previewCsv(csv.replace("Synthetic checking","Missing synthetic account"));
    imports.acceptImport(new TransactionImportService.ImportAcceptRequest(preview.importId(),List.of("R1")));
    var history=imports.getHistory().stream().filter(h->h.importId().equals(preview.importId())).findFirst().orElseThrow();
    long before=transactions.count();
    assertThrows(IllegalArgumentException.class,()->imports.resolveHistory(
        new TransactionImportService.ResolveHistoryRequest(history.historyId(),"CREATE_FUTURE",null,"Synthetic","2026-11-01")));
    assertEquals(before,transactions.count());
    assertEquals(history,imports.getHistory().stream().filter(h->h.historyId().equals(history.historyId())).findFirst().orElseThrow());
    assertEquals(100,accounts.findById("synthetic").orElseThrow().getBalance());
  }
}
