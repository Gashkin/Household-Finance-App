package com.householdfinance.mcp;

import com.householdfinance.forecast.ForecastService;
import com.householdfinance.ledger.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * An unavailable optional projection must not mark the enclosing snapshot rollback-only. The
 * workspace admission read lock spans all these transactions, so app edits/restore cannot
 * interleave. Each method is an explicit read allowlist, not an API proxy.
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class McpOptionalReads {
  private final DebtService debts;
  private final CommitmentService commitments;
  private final ForecastService plans;

  public McpOptionalReads(DebtService debts, CommitmentService commitments, ForecastService plans) {
    this.debts = debts;
    this.commitments = commitments;
    this.plans = plans;
  }

  public DebtService.Workspace debts() {
    return debts.get();
  }

  public List<CommitmentService.Occurrence> debtObligations(
      LocalDate start, LocalDate end, LocalDate today) {
    return debts.obligations(start, end, today);
  }

  public ForecastService.Workspace savedPlan() {
    return plans.get();
  }

  public CommitmentService.Outlook outlook(LocalDate today, LocalDate end) {
    return commitments.outlook(today, end, false, false);
  }
}
