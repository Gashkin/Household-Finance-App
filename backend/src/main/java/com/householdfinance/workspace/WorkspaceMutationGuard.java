package com.householdfinance.workspace;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Single-process admission for every application database write, not only plans. */
@Component
public class WorkspaceMutationGuard implements TransactionExecutionListener {
  private final ReentrantLock writes=new ReentrantLock(true);
  private final ObjectProvider<WorkspaceBackupService> backups;

  public WorkspaceMutationGuard(ObjectProvider<WorkspaceBackupService> backups) {
    this.backups=backups;
  }

  private boolean guarded(TransactionExecution tx) {
    return tx.isNewTransaction() && !tx.isReadOnly();
  }

  @Override public void beforeBegin(TransactionExecution tx) {
    // Acquire before the database transaction starts, so concurrent writers do
    // not retain stale snapshots while waiting for the shared capacity budget.
    if (guarded(tx)) writes.lock();
  }

  @Override public void afterBegin(TransactionExecution tx, Throwable failure) {
    if (!guarded(tx)) return;
    if (failure!=null) { writes.unlock(); return; }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void beforeCommit(boolean readOnly) {
        backups.getObject().assertMutationRecoverable();
      }
      @Override public void afterCompletion(int status) { writes.unlock(); }
    });
  }
}
