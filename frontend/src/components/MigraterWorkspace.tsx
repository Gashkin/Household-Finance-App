import { useState } from 'react';

interface MigrationRow {
  rowId: string;
  sheet: string;
  metric: string;
  baselineAmount: number;
  appAmount: number;
  variance: number;
  status: string;
}

interface MigrationPreview {
  importId: string;
  baselineTotal: number;
  appTotal: number;
  variance: number;
  matchedCount: number;
  mismatchedCount: number;
  rows: MigrationRow[];
}

interface MigrationReconcileResult {
  importId: string;
  acceptedCount: number;
  mismatchCount: number;
  notes: string;
}

interface MigrationSheetReport {
  sheet: string;
  totalRows: number;
  mismatchRows: number;
  unresolvedRows: number;
  baselineTotal: number;
  appTotal: number;
  varianceTotal: number;
}

interface MigrationExecutionReport {
  importId: string;
  totalRows: number;
  mismatchedRows: number;
  unresolvedRows: number;
  sheets: MigrationSheetReport[];
}

interface MigrationArtifactHistoryEntry {
  id: number;
  importId: string;
  artifactType: string;
  createdAt: string;
  payload: string;
}

interface WorkbookSheetPreview {
  sheetName: string;
  rowCount: number;
  headers: string[];
  sampleRows: string[][];
}

interface WorkbookPreview {
  importId: string;
  sheetCount: number;
  totalRowCount: number;
  sheets: WorkbookSheetPreview[];
}

const isTargetedWorkbookSheet = (sheetName: string): boolean => {
  const normalized = sheetName.trim().toLowerCase();
  return normalized === 'monthly budget' || normalized.endsWith('26');
};

interface MappingWarning {
  sheetName: string;
  rowNumber: number;
  message: string;
}

interface MappedAccount {
  name: string;
  type: string;
  balance: number;
  sourceSheet: string;
  sourceRow: number;
}

interface MappedCategory {
  name: string;
  budget: string;
  sourceSheet: string;
  sourceRow: number;
}

interface MappedCommitment {
  title: string;
  amount: number;
  dueDate: string;
  sourceSheet: string;
  sourceRow: number;
}

interface MappedTransaction {
  date: string;
  description: string;
  amount: number;
  account: string;
  sourceSheet: string;
  sourceRow: number;
}

interface WorkbookMappingResult {
  importId: string;
  sheetCount: number;
  mappedAccountsCount: number;
  mappedCategoriesCount: number;
  mappedCommitmentsCount: number;
  mappedTransactionsCount: number;
  recurringCommitmentsCount: number;
  oneOffCommitmentsCount: number;
  targetingRulesApplied: string[];
  unmappedSheets: string[];
  warnings: MappingWarning[];
  accounts: MappedAccount[];
  categories: MappedCategory[];
  commitments: MappedCommitment[];
  transactions: MappedTransaction[];
}

interface ValidationIssue {
  component: string;
  rowReference: string;
  severity: 'ERROR' | 'WARNING';
  message: string;
}

interface LoadComponentCounts {
  accounts: number;
  categories: number;
  commitments: number;
  transactions: number;
}

interface WorkbookLoadValidationResult {
  validationId: string;
  mode: 'MERGE' | 'REPLACE';
  valid: boolean;
  hardErrorCount: number;
  warningCount: number;
  counts: LoadComponentCounts;
  issues: ValidationIssue[];
  confirmationToken: string;
}

interface WorkbookLoadExecutionResult {
  executionId: string;
  validationId: string;
  mode: 'MERGE' | 'REPLACE';
  appliedAccounts: number;
  appliedCategories: number;
  appliedCommitments: number;
  skippedTransactions: number;
  notes: string;
}

interface WorkbookLoadPostSummary {
  executionId: string;
  validationId: string;
  mode: 'MERGE' | 'REPLACE';
  status: 'APPLIED' | 'ROLLED_BACK';
  createdAt: string;
  appliedAccounts: number;
  appliedCategories: number;
  appliedCommitments: number;
  rejectedRows: number;
  notes: string;
}

interface LoadRejectedRow {
  component: string;
  rowReference: string;
  reason: string;
}

interface WorkbookLoadRollbackResult {
  executionId: string;
  status: 'ROLLED_BACK';
  message: string;
}

const sampleMigrationCsv = `sheet,metric,amount
MonthlyBudget,Checking,4200.00
MonthlyBudget,Savings,12500.00
MonthlyBudget,Rent,1200.00
MonthlyBudget,Internet,70.00
MonthlyBudget,Net Position,15870.00`;

function MigraterWorkspace() {
  const [migrationCsvInput, setMigrationCsvInput] = useState(sampleMigrationCsv);
  const [migrationPreview, setMigrationPreview] = useState<MigrationPreview | null>(null);
  const [selectedMigrationRowIds, setSelectedMigrationRowIds] = useState<string[]>([]);
  const [migrationStatus, setMigrationStatus] = useState('');
  const [isMigrationSubmitting, setIsMigrationSubmitting] = useState(false);
  const [migrationReport, setMigrationReport] = useState<MigrationExecutionReport | null>(null);
  const [migrationHistory, setMigrationHistory] = useState<MigrationArtifactHistoryEntry[]>([]);
  const [isReportLoading, setIsReportLoading] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);

  const [workbookFile, setWorkbookFile] = useState<File | null>(null);
  const [workbookPreview, setWorkbookPreview] = useState<WorkbookPreview | null>(null);
  const [mappingResult, setMappingResult] = useState<WorkbookMappingResult | null>(null);
  const [loadMode, setLoadMode] = useState<'MERGE' | 'REPLACE'>('MERGE');
  const [validationResult, setValidationResult] = useState<WorkbookLoadValidationResult | null>(null);
  const [executionResult, setExecutionResult] = useState<WorkbookLoadExecutionResult | null>(null);
  const [latestSummary, setLatestSummary] = useState<WorkbookLoadPostSummary | null>(null);
  const [rejectedRows, setRejectedRows] = useState<LoadRejectedRow[]>([]);
  const [workbookStatus, setWorkbookStatus] = useState('');
  const [isWorkbookSubmitting, setIsWorkbookSubmitting] = useState(false);
  const [isMappingSubmitting, setIsMappingSubmitting] = useState(false);
  const [isValidationSubmitting, setIsValidationSubmitting] = useState(false);
  const [isExecutionSubmitting, setIsExecutionSubmitting] = useState(false);
  const [isSummaryLoading, setIsSummaryLoading] = useState(false);
  const [isRejectionsLoading, setIsRejectionsLoading] = useState(false);
  const [isRollbackSubmitting, setIsRollbackSubmitting] = useState(false);

  const targetedWorkbookSheets = workbookPreview?.sheets.filter((sheet) => isTargetedWorkbookSheet(sheet.sheetName)) ?? [];
  const ignoredWorkbookSheetCount = workbookPreview ? workbookPreview.sheets.length - targetedWorkbookSheets.length : 0;

  const previewMigration = async () => {
    setIsMigrationSubmitting(true);
    setMigrationStatus('');

    try {
      const response = await fetch('http://localhost:8080/migration/preview', {
        method: 'POST',
        headers: {
          'Content-Type': 'text/csv',
        },
        body: migrationCsvInput,
      });

      if (!response.ok) {
        throw new Error('Migration preview request failed.');
      }

      const data = (await response.json()) as MigrationPreview;
      setMigrationPreview(data);
      setMigrationReport(null);
      setMigrationHistory([]);
      setSelectedMigrationRowIds(data.rows.map((row) => row.rowId));
      setMigrationStatus(`Migration preview ready. ${data.matchedCount} matched, ${data.mismatchedCount} mismatched.`);
    } catch {
      setMigrationStatus('Unable to preview migration baseline. Ensure backend is running on port 8080.');
    } finally {
      setIsMigrationSubmitting(false);
    }
  };

  const toggleMigrationSelection = (rowId: string) => {
    setSelectedMigrationRowIds((current) =>
      current.includes(rowId) ? current.filter((id) => id !== rowId) : [...current, rowId]
    );
  };

  const reconcileSelectedRows = async () => {
    if (!migrationPreview || selectedMigrationRowIds.length === 0) {
      setMigrationStatus('Select at least one migration row before reconciling.');
      return;
    }

    setIsMigrationSubmitting(true);
    try {
      const response = await fetch('http://localhost:8080/migration/reconcile', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          importId: migrationPreview.importId,
          acceptedRowIds: selectedMigrationRowIds,
        }),
      });

      if (!response.ok) {
        throw new Error('Migration reconcile request failed.');
      }

      const result = (await response.json()) as MigrationReconcileResult;
      setMigrationStatus(
        `Reconciliation recorded for ${result.acceptedCount} rows (${result.mismatchCount} mismatches).`
      );
    } catch {
      setMigrationStatus('Unable to record reconciliation. Ensure backend is running on port 8080.');
    } finally {
      setIsMigrationSubmitting(false);
    }
  };

  const loadMigrationReport = async () => {
    if (!migrationPreview) {
      setMigrationStatus('Preview migration first before loading execution report.');
      return;
    }

    setIsReportLoading(true);
    try {
      const response = await fetch(`http://localhost:8080/migration/report/${migrationPreview.importId}`);
      if (!response.ok) {
        throw new Error('Report request failed.');
      }

      const data = (await response.json()) as MigrationExecutionReport;
      setMigrationReport(data);
      setMigrationStatus(`Execution report loaded. ${data.mismatchedRows} mismatches, ${data.unresolvedRows} unresolved.`);
    } catch {
      setMigrationStatus('Unable to load migration report. Reconcile first and ensure backend is running.');
    } finally {
      setIsReportLoading(false);
    }
  };

  const loadMigrationHistory = async () => {
    if (!migrationPreview) {
      setMigrationStatus('Preview migration first before loading artifact history.');
      return;
    }

    setIsHistoryLoading(true);
    try {
      const response = await fetch(`http://localhost:8080/migration/history/${migrationPreview.importId}`);
      if (!response.ok) {
        throw new Error('History request failed.');
      }

      const data = (await response.json()) as MigrationArtifactHistoryEntry[];
      setMigrationHistory(data);
      setMigrationStatus(`Loaded ${data.length} artifact history entries.`);
    } catch {
      setMigrationStatus('Unable to load migration history. Ensure backend is running.');
    } finally {
      setIsHistoryLoading(false);
    }
  };

  const previewWorkbook = async () => {
    if (!workbookFile) {
      setWorkbookStatus('Select an .xlsx workbook file first.');
      return;
    }

    setIsWorkbookSubmitting(true);
    setWorkbookStatus('');

    try {
      const formData = new FormData();
      formData.append('file', workbookFile);

      const response = await fetch('http://localhost:8080/migration/workbook/preview', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error('Workbook preview failed.');
      }

      const data = (await response.json()) as WorkbookPreview;
      setWorkbookPreview(data);
      setMappingResult(null);
      setValidationResult(null);
      setExecutionResult(null);
      const targetedCount = data.sheets.filter((sheet) => isTargetedWorkbookSheet(sheet.sheetName)).length;
      const ignoredCount = data.sheets.length - targetedCount;
      setWorkbookStatus(
        `Workbook parsed: ${data.sheetCount} tabs discovered, ${targetedCount} targeted for load, ${ignoredCount} ignored.`
      );
    } catch {
      setWorkbookStatus('Unable to parse workbook. Ensure backend is running and file is a valid .xlsx workbook.');
    } finally {
      setIsWorkbookSubmitting(false);
    }
  };

  const mapWorkbookToDomain = async () => {
    if (!workbookFile) {
      setWorkbookStatus('Select an .xlsx workbook file first.');
      return;
    }

    setIsMappingSubmitting(true);
    setWorkbookStatus('');

    try {
      const formData = new FormData();
      formData.append('file', workbookFile);

      const response = await fetch('http://localhost:8080/migration/workbook/map', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error('Workbook mapping failed.');
      }

      const data = (await response.json()) as WorkbookMappingResult;
      setMappingResult(data);
      setValidationResult(null);
      setExecutionResult(null);
      setWorkbookStatus(
        `Mapped ${data.mappedAccountsCount} accounts, ${data.mappedCategoriesCount} categories, ${data.mappedCommitmentsCount} commitments, ${data.mappedTransactionsCount} transactions.`
      );
    } catch {
      setWorkbookStatus('Unable to map workbook. Ensure backend is running and file is a valid workbook.');
    } finally {
      setIsMappingSubmitting(false);
    }
  };

  const validateWorkbookLoad = async () => {
    if (!workbookFile) {
      setWorkbookStatus('Select an .xlsx workbook file first.');
      return;
    }

    setIsValidationSubmitting(true);
    setWorkbookStatus('');
    try {
      const formData = new FormData();
      formData.append('file', workbookFile);
      formData.append('mode', loadMode);

      const response = await fetch('http://localhost:8080/migration/workbook/validate', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error('Workbook validation failed.');
      }

      const data = (await response.json()) as WorkbookLoadValidationResult;
      setValidationResult(data);
      setExecutionResult(null);
      setWorkbookStatus(
        data.valid
          ? `Validation passed with ${data.warningCount} warnings. Confirm to execute ${data.mode} load.`
          : `Validation failed with ${data.hardErrorCount} hard errors.`
      );
    } catch {
      setWorkbookStatus('Unable to validate workbook load. Ensure backend is running.');
    } finally {
      setIsValidationSubmitting(false);
    }
  };

  const executeValidatedLoad = async () => {
    if (!validationResult || !validationResult.valid || !validationResult.confirmationToken) {
      setWorkbookStatus('Run validation successfully before executing load.');
      return;
    }

    setIsExecutionSubmitting(true);
    setWorkbookStatus('');
    try {
      const response = await fetch('http://localhost:8080/migration/workbook/execute', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          validationId: validationResult.validationId,
          confirmationToken: validationResult.confirmationToken,
          mode: validationResult.mode,
        }),
      });

      if (!response.ok) {
        throw new Error('Workbook load execution failed.');
      }

      const data = (await response.json()) as WorkbookLoadExecutionResult;
      setExecutionResult(data);
      setLatestSummary(null);
      setRejectedRows([]);
      setWorkbookStatus(data.notes);
    } catch {
      setWorkbookStatus('Unable to execute workbook load. Re-run validation and try again.');
    } finally {
      setIsExecutionSubmitting(false);
    }
  };

  const loadLatestSummary = async () => {
    setIsSummaryLoading(true);
    try {
      const response = await fetch('http://localhost:8080/migration/workbook/loads/latest');
      if (!response.ok) {
        throw new Error('Load summary request failed.');
      }

      const data = (await response.json()) as WorkbookLoadPostSummary;
      setLatestSummary(data);
      setWorkbookStatus(`Loaded summary for ${data.executionId} (${data.status}).`);
    } catch {
      setWorkbookStatus('Unable to load post-load summary. Execute a load first.');
    } finally {
      setIsSummaryLoading(false);
    }
  };

  const loadRejectedRows = async () => {
    const executionId = latestSummary?.executionId ?? executionResult?.executionId;
    if (!executionId) {
      setWorkbookStatus('Load summary or execute a load before requesting rejected rows.');
      return;
    }

    setIsRejectionsLoading(true);
    try {
      const response = await fetch(`http://localhost:8080/migration/workbook/loads/${executionId}/rejections`);
      if (!response.ok) {
        throw new Error('Rejected rows request failed.');
      }

      const data = (await response.json()) as LoadRejectedRow[];
      setRejectedRows(data);
      setWorkbookStatus(`Loaded ${data.length} rejected rows for ${executionId}.`);
    } catch {
      setWorkbookStatus('Unable to load rejected rows.');
    } finally {
      setIsRejectionsLoading(false);
    }
  };

  const rollbackLatestLoad = async () => {
    const executionId = latestSummary?.executionId ?? executionResult?.executionId;
    if (!executionId) {
      setWorkbookStatus('Load summary or execute a load before rollback.');
      return;
    }

    setIsRollbackSubmitting(true);
    try {
      const response = await fetch(`http://localhost:8080/migration/workbook/loads/${executionId}/rollback`, {
        method: 'POST',
      });

      if (!response.ok) {
        throw new Error('Rollback request failed.');
      }

      const result = (await response.json()) as WorkbookLoadRollbackResult;
      setWorkbookStatus(result.message);
      await loadLatestSummary();
    } catch {
      setWorkbookStatus('Unable to rollback load. It may already be rolled back.');
    } finally {
      setIsRollbackSubmitting(false);
    }
  };

  return (
    <section>
      <h2>Migrater workspace</h2>
      <p>Run workbook ingestion, mapping, and migration reconciliation workflows.</p>

      <div className="workspace-card import-card migration-card">
        <h3>Spreadsheet migration baseline reconciliation</h3>
        <p>Paste baseline CSV and compare spreadsheet amounts with current app values.</p>

        <textarea
          className="import-input"
          value={migrationCsvInput}
          onChange={(event) => setMigrationCsvInput(event.target.value)}
          rows={7}
        />

        <div className="import-actions">
          <button type="button" onClick={previewMigration} disabled={isMigrationSubmitting}>
            Preview baseline variance
          </button>
          <button type="button" onClick={reconcileSelectedRows} disabled={isMigrationSubmitting || !migrationPreview}>
            Record reconciliation
          </button>
          <button type="button" onClick={loadMigrationReport} disabled={isReportLoading || !migrationPreview}>
            Load execution report
          </button>
          <button type="button" onClick={loadMigrationHistory} disabled={isHistoryLoading || !migrationPreview}>
            Load artifact history
          </button>
        </div>

        {migrationStatus && <p className="import-status">{migrationStatus}</p>}

        {migrationPreview && (
          <>
            <div className="migration-summary">
              <span>Baseline total: {migrationPreview.baselineTotal.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</span>
              <span>App total: {migrationPreview.appTotal.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</span>
              <span>Variance: {migrationPreview.variance.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</span>
            </div>

            <table>
              <thead>
                <tr>
                  <th>Use</th>
                  <th>Sheet</th>
                  <th>Metric</th>
                  <th>Baseline</th>
                  <th>App</th>
                  <th>Variance</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {migrationPreview.rows.map((row) => (
                  <tr key={row.rowId} className={row.status === 'Mismatch' ? 'duplicate-row' : ''}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedMigrationRowIds.includes(row.rowId)}
                        onChange={() => toggleMigrationSelection(row.rowId)}
                      />
                    </td>
                    <td>{row.sheet}</td>
                    <td>{row.metric}</td>
                    <td>{row.baselineAmount.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                    <td>{row.appAmount.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                    <td>{row.variance.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                    <td>{row.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}

        {migrationReport && (
          <div className="workbook-preview-stack">
            <div className="workspace-card workbook-sheet-card">
              <h4>Execution report: {migrationReport.importId}</h4>
              <div className="migration-summary">
                <span>Total rows: {migrationReport.totalRows}</span>
                <span>Mismatched rows: {migrationReport.mismatchedRows}</span>
                <span>Unresolved rows: {migrationReport.unresolvedRows}</span>
              </div>

              <table>
                <thead>
                  <tr>
                    <th>Sheet</th>
                    <th>Total</th>
                    <th>Mismatch</th>
                    <th>Unresolved</th>
                    <th>Baseline</th>
                    <th>App</th>
                    <th>Variance</th>
                  </tr>
                </thead>
                <tbody>
                  {migrationReport.sheets.map((sheet) => (
                    <tr key={sheet.sheet}>
                      <td>{sheet.sheet}</td>
                      <td>{sheet.totalRows}</td>
                      <td>{sheet.mismatchRows}</td>
                      <td>{sheet.unresolvedRows}</td>
                      <td>{sheet.baselineTotal.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                      <td>{sheet.appTotal.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                      <td>{sheet.varianceTotal.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {migrationHistory.length > 0 && (
          <div className="workbook-preview-stack">
            <div className="workspace-card workbook-sheet-card">
              <h4>Artifact history</h4>
              <table>
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Type</th>
                    <th>Artifact Id</th>
                    <th>Payload</th>
                  </tr>
                </thead>
                <tbody>
                  {migrationHistory.map((entry) => (
                    <tr key={entry.id}>
                      <td>{new Date(entry.createdAt).toLocaleString()}</td>
                      <td>{entry.artifactType}</td>
                      <td>{entry.id}</td>
                      <td className="artifact-payload">
                        {entry.payload.slice(0, 180)}
                        {entry.payload.length > 180 ? '...' : ''}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <div className="workspace-card import-card migration-card">
        <h3>Workbook upload and sheet extraction (.xlsx)</h3>
        <p>Upload workbook and preview only load-target tabs: Monthly Budget and tabs ending in 26.</p>

        <input
          className="file-input"
          type="file"
          accept=".xlsx,.xlsm,.xltx,.xltm"
          onChange={(event) => {
            setWorkbookFile(event.target.files?.[0] ?? null);
            setValidationResult(null);
            setExecutionResult(null);
          }}
        />

        <div className="migration-summary">
          <span>Load mode:</span>
          <select value={loadMode} onChange={(event) => setLoadMode(event.target.value as 'MERGE' | 'REPLACE')}>
            <option value="MERGE">MERGE (upsert)</option>
            <option value="REPLACE">REPLACE (overwrite planner data)</option>
          </select>
        </div>

        <div className="import-actions">
          <button type="button" onClick={previewWorkbook} disabled={isWorkbookSubmitting}>
            Preview workbook sheets
          </button>
          <button type="button" onClick={mapWorkbookToDomain} disabled={isMappingSubmitting}>
            Map workbook to components
          </button>
          <button type="button" onClick={validateWorkbookLoad} disabled={isValidationSubmitting}>
            Validate load (dry-run)
          </button>
          <button
            type="button"
            onClick={executeValidatedLoad}
            disabled={isExecutionSubmitting || !validationResult?.valid || !validationResult.confirmationToken}
          >
            Confirm and execute load
          </button>
        </div>

        {workbookStatus && <p className="import-status">{workbookStatus}</p>}

        {workbookPreview && (
          <div className="workbook-preview-stack">
            {targetedWorkbookSheets.length > 0 && (
              <p className="import-status">Showing targeted tabs only. Ignored tabs: {ignoredWorkbookSheetCount}</p>
            )}

            {targetedWorkbookSheets.length === 0 && (
              <p className="import-status">No targeted tabs found. Add Monthly Budget and/or tabs ending in 26.</p>
            )}

            {targetedWorkbookSheets.map((sheet) => (
              <div key={sheet.sheetName} className="workspace-card workbook-sheet-card">
                <h4>{sheet.sheetName}</h4>
                <p>Rows: {sheet.rowCount}</p>

                {sheet.headers.length > 0 && (
                  <table>
                    <thead>
                      <tr>
                        {sheet.headers.map((header, index) => (
                          <th key={`${sheet.sheetName}-header-${index}`}>{header || `Column ${index + 1}`}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {sheet.sampleRows.map((row, rowIndex) => (
                        <tr key={`${sheet.sheetName}-row-${rowIndex}`}>
                          {sheet.headers.map((_, cellIndex) => (
                            <td key={`${sheet.sheetName}-cell-${rowIndex}-${cellIndex}`}>{row[cellIndex] ?? ''}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            ))}
          </div>
        )}

        {mappingResult && (
          <div className="workbook-preview-stack">
            <div className="workspace-card workbook-sheet-card">
              <h4>Mapping summary</h4>
              <div className="migration-summary">
                <span>Accounts: {mappingResult.mappedAccountsCount}</span>
                <span>Categories: {mappingResult.mappedCategoriesCount}</span>
                <span>Commitments: {mappingResult.mappedCommitmentsCount}</span>
                <span>Recurring commitments: {mappingResult.recurringCommitmentsCount}</span>
                <span>One-off commitments: {mappingResult.oneOffCommitmentsCount}</span>
                <span>Transactions: {mappingResult.mappedTransactionsCount}</span>
              </div>

              {mappingResult.targetingRulesApplied.length > 0 && (
                <p>Targeting rules: {mappingResult.targetingRulesApplied.join(' | ')}</p>
              )}

              {mappingResult.unmappedSheets.length > 0 && <p>Unmapped sheets: {mappingResult.unmappedSheets.join(', ')}</p>}

              {mappingResult.warnings.length > 0 && (
                <ul className="mapping-warning-list">
                  {mappingResult.warnings.map((warning, index) => (
                    <li key={`${warning.sheetName}-${warning.rowNumber}-${index}`}>
                      {warning.sheetName} row {warning.rowNumber}: {warning.message}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {mappingResult.accounts.length > 0 && (
              <div className="workspace-card workbook-sheet-card">
                <h4>Mapped accounts</h4>
                <table>
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Type</th>
                      <th>Balance</th>
                      <th>Source</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingResult.accounts.map((account, index) => (
                      <tr key={`${account.name}-${account.sourceSheet}-${index}`}>
                        <td>{account.name}</td>
                        <td>{account.type}</td>
                        <td>{account.balance.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                        <td>{account.sourceSheet} #{account.sourceRow}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {mappingResult.categories.length > 0 && (
              <div className="workspace-card workbook-sheet-card">
                <h4>Mapped categories</h4>
                <table>
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Budget</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingResult.categories.map((category, index) => (
                      <tr key={`${category.name}-${index}`}>
                        <td>{category.name}</td>
                        <td>{category.budget}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {mappingResult.commitments.length > 0 && (
              <div className="workspace-card workbook-sheet-card">
                <h4>Mapped commitments</h4>
                <table>
                  <thead>
                    <tr>
                      <th>Title</th>
                      <th>Amount</th>
                      <th>Due date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingResult.commitments.map((commitment, index) => (
                      <tr key={`${commitment.title}-${index}`}>
                        <td>{commitment.title}</td>
                        <td>{commitment.amount.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                        <td>{commitment.dueDate || 'N/A'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {mappingResult.transactions.length > 0 && (
              <div className="workspace-card workbook-sheet-card">
                <h4>Mapped transactions</h4>
                <table>
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Description</th>
                      <th>Amount</th>
                      <th>Account</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingResult.transactions.map((transaction, index) => (
                      <tr key={`${transaction.description}-${transaction.date}-${index}`}>
                        <td>{transaction.date || 'N/A'}</td>
                        <td>{transaction.description}</td>
                        <td>{transaction.amount.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                        <td>{transaction.account || 'N/A'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {validationResult && (
          <div className="workbook-preview-stack">
            <div className="workspace-card workbook-sheet-card">
              <h4>Load validation ({validationResult.mode})</h4>
              <div className="migration-summary">
                <span>Accounts: {validationResult.counts.accounts}</span>
                <span>Categories: {validationResult.counts.categories}</span>
                <span>Commitments: {validationResult.counts.commitments}</span>
                <span>Transactions: {validationResult.counts.transactions}</span>
                <span>Errors: {validationResult.hardErrorCount}</span>
                <span>Warnings: {validationResult.warningCount}</span>
              </div>

              <p className={validationResult.valid ? 'cashflow-positive' : 'cashflow-negative'}>
                {validationResult.valid
                  ? 'Validation passed. Confirmation token generated and ready for execute.'
                  : 'Validation failed. Resolve hard errors before execute.'}
              </p>

              {validationResult.issues.length > 0 && (
                <table>
                  <thead>
                    <tr>
                      <th>Severity</th>
                      <th>Component</th>
                      <th>Row</th>
                      <th>Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    {validationResult.issues.map((issue, index) => (
                      <tr key={`${issue.component}-${issue.rowReference}-${index}`} className={issue.severity === 'ERROR' ? 'duplicate-row' : ''}>
                        <td>{issue.severity}</td>
                        <td>{issue.component}</td>
                        <td>{issue.rowReference}</td>
                        <td>{issue.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}

        {executionResult && (
          <div className="workbook-preview-stack">
            <div className="workspace-card workbook-sheet-card">
              <h4>Load execution summary</h4>
              <div className="migration-summary">
                <span>Execution Id: {executionResult.executionId}</span>
                <span>Mode: {executionResult.mode}</span>
                <span>Applied accounts: {executionResult.appliedAccounts}</span>
                <span>Applied categories: {executionResult.appliedCategories}</span>
                <span>Applied commitments: {executionResult.appliedCommitments}</span>
                <span>Skipped transactions: {executionResult.skippedTransactions}</span>
              </div>
              <p>{executionResult.notes}</p>
            </div>
          </div>
        )}

        <div className="workbook-preview-stack">
          <div className="workspace-card workbook-sheet-card">
            <h4>Post-load verification dashboard</h4>
            <div className="import-actions">
              <button type="button" onClick={loadLatestSummary} disabled={isSummaryLoading}>
                Load latest summary
              </button>
              <button type="button" onClick={loadRejectedRows} disabled={isRejectionsLoading}>
                Load rejected rows
              </button>
              <button type="button" onClick={rollbackLatestLoad} disabled={isRollbackSubmitting}>
                Rollback latest load
              </button>
            </div>

            {latestSummary && (
              <div className="migration-summary">
                <span>Execution: {latestSummary.executionId}</span>
                <span>Status: {latestSummary.status}</span>
                <span>Applied accounts: {latestSummary.appliedAccounts}</span>
                <span>Applied categories: {latestSummary.appliedCategories}</span>
                <span>Applied commitments: {latestSummary.appliedCommitments}</span>
                <span>Rejected rows: {latestSummary.rejectedRows}</span>
              </div>
            )}

            {latestSummary && <p>{latestSummary.notes}</p>}

            {rejectedRows.length > 0 && (
              <table>
                <thead>
                  <tr>
                    <th>Component</th>
                    <th>Row</th>
                    <th>Reason</th>
                  </tr>
                </thead>
                <tbody>
                  {rejectedRows.map((row, index) => (
                    <tr key={`${row.component}-${row.rowReference}-${index}`}>
                      <td>{row.component}</td>
                      <td>{row.rowReference}</td>
                      <td>{row.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

export default MigraterWorkspace;
