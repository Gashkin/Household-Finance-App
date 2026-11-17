package com.householdfinance.workspace;

import static org.junit.jupiter.api.Assertions.*;
import com.householdfinance.service.MigrationArtifactService;
import com.householdfinance.service.CashPositionService;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workspace-capacity;DB_CLOSE_DELAY=-1"})
@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class WorkspaceCapacityTest {
  @Autowired MigrationArtifactService artifacts;
  @Autowired WorkspaceBackupService backups;
  @Autowired JdbcTemplate jdbc;
  @Autowired CashPositionService cash;

  @BeforeEach void clear() {
    var tables=new ArrayList<>(WorkspaceBackupService.TABLES);
    Collections.reverse(tables);
    tables.forEach(t->jdbc.update("DELETE FROM "+t));
  }

  @Test void nonPlanWritesCannotCommitAnUnrecoverableWorkspace() {
    artifacts.recordArtifact("kept","SYNTHETIC", "x".repeat(1_100_000));
    var before=backups.counts();
    assertThrows(IllegalArgumentException.class,
        ()->artifacts.recordArtifact("rejected","SYNTHETIC", "y".repeat(800_000)));
    assertEquals(before,backups.counts());
    assertNull(artifacts.getLatestPayload("rejected","SYNTHETIC",String.class));
    byte[] archive=backups.exportBackup("synthetic-separate-backup-password");
    clear();
    assertEquals(before,backups.restore(archive,"synthetic-separate-backup-password","RESTORE INTO EMPTY WORKSPACE"));
    assertEquals("x".repeat(1_100_000),artifacts.getLatestPayload("kept","SYNTHETIC",String.class));
  }

  @Test void repeatedLegalCashSnapshotsStopBeforeBackupBecomesUnrecoverable() {
    var lines=java.util.stream.IntStream.range(0,30)
        .mapToObj(i->new CashPositionService.Line("Synthetic line "+i+"x".repeat(80),100L)).toList();
    var input=new CashPositionService.Input(LocalDate.now(),"Synthetic capacity audit","REVIEWED",lines,lines,0);
    int accepted=0;
    for(int i=0;i<400;i++) {
      try { cash.save(input);accepted++; }
      catch(IllegalArgumentException full) { break; }
    }
    assertTrue(accepted>0 && accepted<400,"The audit's 400-snapshot growth must be rejected before exceeding backup capacity");
    var before=backups.counts();
    assertThrows(IllegalArgumentException.class,()->cash.save(input));
    assertEquals(before,backups.counts(),"Rejected snapshot and its lines must both roll back");
    byte[] archive=backups.exportBackup("synthetic-separate-backup-password");
    clear();
    assertEquals(before,backups.restore(archive,"synthetic-separate-backup-password","RESTORE INTO EMPTY WORKSPACE"));
    assertEquals(30,cash.latest().orElseThrow().cashBalances().size());
  }

  @Test void concurrentNonPlanTransactionsCannotOverbookCapacityAndRollbackReleasesAdmission() throws Exception {
    artifacts.recordArtifact("baseline","SYNTHETIC", "x".repeat(1_100_000));
    var barrier=new CyclicBarrier(2);
    var pool=Executors.newFixedThreadPool(2);
    try {
      Callable<String> write=()->com.householdfinance.access.OwnerFixture.call(()->{
        barrier.await();
        try { artifacts.recordArtifact(UUID.randomUUID().toString(),"SYNTHETIC","y".repeat(400_000)); return "accepted"; }
        catch (IllegalArgumentException e) { return "rejected"; }
      });
      var a=pool.submit(write);var b=pool.submit(write);
      var results=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
      assertEquals(1,Collections.frequency(results,"accepted"));
      assertEquals(1,Collections.frequency(results,"rejected"));
      // A later write on another thread proves rejected commits release admission.
      pool.submit(()->com.householdfinance.access.OwnerFixture.call(()->{artifacts.recordArtifact("after-rollback","SYNTHETIC","small");return null;}))
          .get(10,TimeUnit.SECONDS);
      assertEquals(3L,backups.counts().get("MIGRATION_ARTIFACTS"));
      assertNotNull(backups.exportBackup("synthetic-separate-backup-password"));
    } finally { pool.shutdownNow(); }
  }
}
