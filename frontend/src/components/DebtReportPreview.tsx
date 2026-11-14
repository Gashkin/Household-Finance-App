import type { DebtComparisonReport } from '../api/debtScenarios';
import { money } from '../domain/money';

export default function DebtReportPreview({ report, onDownload }: { report: DebtComparisonReport; onDownload: () => void }) {
  const evidence = report.cashEvidence;
  return <section className="workspace-card report-preview" aria-labelledby="report-preview-title">
    <p className="debt-eyebrow">Portable decision record</p>
    <h3 id="report-preview-title">Review your report before downloading</h3>
    <p>Keep these inputs, results and assumptions together so you can revisit this comparison. Downloading does not save the decision on the server or initiate a payment.</p>
    <dl className="report-inputs">
      <div><dt>Debt outstanding</dt><dd>{money(report.input.balanceMinor)}</dd></div>
      <div><dt>Annual rate (exact)</dt><dd>{report.input.annualRatePercent}%</dd></div>
      <div><dt>Monthly repayment</dt><dd>{money(report.input.monthlyPaymentMinor)}</dd></div>
      <div><dt>Extra repayment now</dt><dd>{money(report.input.extraPaymentMinor)}</dd></div>
      <div><dt>Available cash</dt><dd>{money(report.input.liquidCashMinor)}</dd></div>
      <div><dt>Protected reserve</dt><dd>{money(report.input.minimumReserveMinor)}</dd></div>
    </dl>
    <h4>Cash evidence</h4>
    {evidence ? <>
      <p>{evidence.sourceNote} · Balance date: {evidence.asOfDate} · {evidence.verification === 'REVIEWED' ? 'Manually reviewed' : 'Estimated'}.</p>
      <p className="debt-caption">Self-reported evidence, not bank-verified. Cash excludes listed next-30-day commitments. Snapshot reference: {evidence.id}</p>
      <ul>{evidence.cashBalances.map((line, i) => <li key={`cash-${i}`}>Account — {line.label}: {money(line.amountMinor)}</li>)}
        {evidence.upcomingCommitments.map((line, i) => <li key={`bill-${i}`}>Commitment — {line.label}: {money(line.amountMinor)}</li>)}</ul>
      {evidence.warnings.map(w => <p className="debt-alert" key={w}>{w}</p>)}
    </> : <p>Manual or illustrative cash inputs. No saved cash evidence is linked to this report.</p>}
    <h4>Warnings and limitations</h4>
    {report.result.warnings.length ? <ul>{report.result.warnings.map(w => <li key={w}>{w}</li>)}</ul> : <p>No scenario warnings for these inputs.</p>}
    <ul>{report.result.assumptions.map(a => <li key={a}>{a}</li>)}</ul>
    <p className="debt-caption">Generated {new Date(report.generatedAt).toLocaleString('en-IE')} · {report.currency} · {report.calculationVersion} · {report.schemaVersion}</p>
    <p>The JSON download contains household figures and evidence source details. Store it somewhere private.</p>
    <button type="button" className="debt-primary" onClick={onDownload}>Download JSON report</button>
  </section>;
}
