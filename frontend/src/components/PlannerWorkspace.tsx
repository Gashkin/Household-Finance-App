import { useEffect, useState } from 'react';

interface Account {
  id: string;
  name: string;
  type: string;
  balance: number;
}

interface Category {
  id: string;
  name: string;
  budget: string;
}

interface Commitment {
  id: string;
  title: string;
  amount: number;
  dueDate: string;
  category?: string | null;
}

interface ImportedTransaction {
  rowId: string;
  date: string;
  description: string;
  amount: number;
  account: string;
  duplicate: boolean;
}

interface ImportPreview {
  importId: string;
  transactions: ImportedTransaction[];
}

interface ImportAcceptResult {
  importId: string;
  acceptedCount: number;
  skippedCount: number;
}

interface PlannerData {
  accounts: Account[];
  categories: Category[];
  commitments: Commitment[];
}

type ForecastPeriod = 'this-month';

const commitmentCategoryLookup: Record<string, string> = {
  salary: 'Income',
  groceries: 'Food & Groceries',
  mortgage: 'Housing',
  cu: 'Savings',
  'dog food': 'Pets',
  vodafone: 'Communications',
  allianz: 'Insurance',
  'car insurance': 'Insurance',
  bin: 'Utilities',
  tv: 'Utilities',
  netlix: 'Subscriptions',
  netflix: 'Subscriptions',
  littlewoods: 'Shopping',
  ger: 'Personal',
  'utility bill': 'Utilities',
  laya: 'Health',
  'life insurance': 'Insurance',
  'disney plus': 'Subscriptions',
  disney: 'Subscriptions',
  medicine: 'Health',
  becky: 'Family',
  extra: 'Personal',
  'clothes &house': 'Clothing & Home',
  'clothes & house': 'Clothing & Home',
  tax: 'Tax',
  'private app': 'Subscriptions',
  'college fees': 'Education',
  elmwood: 'Housing',
  prop: 'Housing',
  dentist: 'Health',
};

function normalizeCommitmentTitle(title: string) {
  return title.toLowerCase().replace(/\s+/g, ' ').trim();
}

function getCommitmentCategory(commitment: Commitment) {
  const category = commitment.category?.trim();
  if (category) {
    return category;
  }

  return commitmentCategoryLookup[normalizeCommitmentTitle(commitment.title)] ?? 'Uncategorized';
}

function formatCurrency(value: number) {
  return value.toLocaleString(undefined, { style: 'currency', currency: 'EUR' });
}

function getCurrentMonthLabel(referenceDate: Date) {
  const month = new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' }).format(referenceDate);
  const year = new Intl.DateTimeFormat('en-US', { year: '2-digit', timeZone: 'UTC' }).format(referenceDate);
  return `${month}${year}`;
}

function PlannerWorkspace() {
  const [plannerData, setPlannerData] = useState<PlannerData>({ accounts: [], categories: [], commitments: [] });
  const [forecastPeriod, setForecastPeriod] = useState<ForecastPeriod>('this-month');
  const [csvInput, setCsvInput] = useState('');
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [selectedRowIds, setSelectedRowIds] = useState<string[]>([]);
  const [importStatus, setImportStatus] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isPlannerLoading, setIsPlannerLoading] = useState(false);
  const [plannerError, setPlannerError] = useState('');

  const currentMonthLabel = getCurrentMonthLabel(new Date());
  const visibleCommitments = plannerData.commitments.filter((commitment) => {
    if (forecastPeriod === 'this-month') {
      return commitment.dueDate === currentMonthLabel;
    }

    return true;
  });

  const forecastCommitments = visibleCommitments.map((commitment) => ({
    ...commitment,
    resolvedCategory: getCommitmentCategory(commitment),
  }));

  const categoryTotals = Array.from(
    forecastCommitments.reduce((totals, commitment) => {
      const category = commitment.resolvedCategory;
      const currentTotal = totals.get(category) ?? 0;
      totals.set(category, currentTotal + commitment.amount);
      return totals;
    }, new Map<string, number>()).entries()
  ).sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));

  const openingAccountTotal = plannerData.accounts.reduce((sum, account) => sum + account.balance, 0);
  const totalIncomes = visibleCommitments
    .filter((commitment) => commitment.amount > 0)
    .reduce((sum, commitment) => sum + commitment.amount, 0);
  const totalExpenses = visibleCommitments
    .filter((commitment) => commitment.amount < 0)
    .reduce((sum, commitment) => sum + commitment.amount, 0);
  const endOfPeriodTotal = openingAccountTotal + totalIncomes + totalExpenses;
  const netMovement = totalIncomes + totalExpenses;

  useEffect(() => {
    const loadPlannerData = async () => {
      setIsPlannerLoading(true);
      setPlannerError('');

      try {
        const response = await fetch('http://localhost:8080/planner');
        if (!response.ok) {
          throw new Error('Planner request failed.');
        }

        const data = (await response.json()) as PlannerData;
        setPlannerData(data);
      } catch {
        setPlannerError('Unable to load real planner data. Ensure backend is running on port 8080.');
      } finally {
        setIsPlannerLoading(false);
      }
    };

    void loadPlannerData();
  }, []);

  const previewImport = async () => {
    setIsSubmitting(true);
    setImportStatus('');
    try {
      const response = await fetch('http://localhost:8080/imports/preview', {
        method: 'POST',
        headers: {
          'Content-Type': 'text/csv',
        },
        body: csvInput,
      });

      if (!response.ok) {
        throw new Error('Preview request failed.');
      }

      const data = (await response.json()) as ImportPreview;
      setPreview(data);
      setSelectedRowIds(data.transactions.filter((transaction) => !transaction.duplicate).map((transaction) => transaction.rowId));
      setImportStatus(`Preview loaded with ${data.transactions.length} rows.`);
    } catch {
      setImportStatus('Unable to preview import. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleSelection = (rowId: string) => {
    setSelectedRowIds((current) =>
      current.includes(rowId) ? current.filter((id) => id !== rowId) : [...current, rowId]
    );
  };

  const acceptSelected = async () => {
    if (!preview || selectedRowIds.length === 0) {
      setImportStatus('Select at least one row before accepting import.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch('http://localhost:8080/imports/accept', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          importId: preview.importId,
          acceptedRowIds: selectedRowIds,
        }),
      });

      if (!response.ok) {
        throw new Error('Accept request failed.');
      }

      const result = (await response.json()) as ImportAcceptResult;
      setImportStatus(`Import accepted: ${result.acceptedCount} rows from batch ${result.importId}.`);
    } catch {
      setImportStatus('Unable to accept import. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section>
      <h2>Planner workspace</h2>
      <p>Manage accounts, categories, commitments, and transaction imports for the planner role.</p>

      <div className="workspace-card forecast-card">
        <div className="forecast-title-row">
          <div>
            <h3>Forecast working</h3>
            <p>Trial balance style view for the current month.</p>
          </div>
          <div className="forecast-controls">
            <label htmlFor="forecast-period">Forecast</label>
            <select
              id="forecast-period"
              value={forecastPeriod}
              onChange={(event) => setForecastPeriod(event.target.value as ForecastPeriod)}
            >
              <option value="this-month">This month</option>
            </select>
          </div>
        </div>

        <div className="forecast-formula">
          <span>Working:</span>
          <strong>{formatCurrency(openingAccountTotal)} + {formatCurrency(totalIncomes)} + {formatCurrency(totalExpenses)} = {formatCurrency(endOfPeriodTotal)}</strong>
        </div>

        <div className="forecast-summary-grid">
          <div className="summary-card">
            <h3>Opening balances</h3>
            <p>{formatCurrency(openingAccountTotal)}</p>
          </div>
          <div className="summary-card highlight">
            <h3>Total incomes</h3>
            <p className="cashflow-positive">{formatCurrency(totalIncomes)}</p>
          </div>
          <div className="summary-card highlight-negative">
            <h3>Total expenses</h3>
            <p className="cashflow-negative">{formatCurrency(totalExpenses)}</p>
          </div>
          <div className="summary-card highlight">
            <h3>End of period total</h3>
            <p>{formatCurrency(endOfPeriodTotal)}</p>
          </div>
        </div>

        <div className="forecast-controls">
          <span className="forecast-note">This month excludes recurring items already counted elsewhere.</span>
        </div>
      </div>

      <div className="planner-grid">
        <div className="workspace-card">
          <h3>Accounts</h3>
          {isPlannerLoading && <p>Loading real account data...</p>}
          {!isPlannerLoading && plannerError && <p>{plannerError}</p>}
          <p className="ledger-caption">Opening balances + current month income and expenses = end of period total.</p>
          <table>
            <thead>
              <tr>
                <th>Working line</th>
                <th>Amount</th>
                <th>Maths</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Opening account balances</td>
                <td>{formatCurrency(openingAccountTotal)}</td>
                <td>{plannerData.accounts.map((account) => `${account.name} ${formatCurrency(account.balance)}`).join(' + ')}</td>
              </tr>
              <tr>
                <td>Total incomes</td>
                <td className="cashflow-positive">{formatCurrency(totalIncomes)}</td>
                <td>Positive commitments in the selected period</td>
              </tr>
              <tr>
                <td>Total expenses</td>
                <td className="cashflow-negative">{formatCurrency(totalExpenses)}</td>
                <td>Negative commitments in the selected period</td>
              </tr>
              <tr className="ledger-total-row">
                <td>End of period total</td>
                <td>{formatCurrency(endOfPeriodTotal)}</td>
                <td>{formatCurrency(openingAccountTotal)} + {formatCurrency(totalIncomes)} + {formatCurrency(totalExpenses)}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="workspace-card">
          <h3>Categories</h3>
          {isPlannerLoading && <p>Loading real category data...</p>}
          <p className="ledger-caption">Each category is the sum of the commitments assigned to it in the selected period.</p>
          <table>
            <thead>
              <tr>
                <th>Category</th>
                <th>Working</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {categoryTotals.map(([categoryName, total]) => (
                <tr key={categoryName}>
                  <td>{categoryName}</td>
                  <td>{`Sum of commitments for ${categoryName}`}</td>
                  <td>{formatCurrency(total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="workspace-card">
          <h3>Commitments</h3>
          {isPlannerLoading && <p>Loading real commitment data...</p>}
          <p className="ledger-caption">Filtered to {forecastPeriod === 'this-month' ? currentMonthLabel : 'all periods'}.</p>
          <table>
            <thead>
              <tr>
                <th>Commitment</th>
                <th>Amount</th>
                <th>Due</th>
                <th>Category</th>
              </tr>
            </thead>
            <tbody>
              {forecastCommitments.map((commitment) => (
                <tr key={commitment.id}>
                  <td>{commitment.title}</td>
                  <td>{formatCurrency(commitment.amount)}</td>
                  <td>{commitment.dueDate}</td>
                  <td>{commitment.resolvedCategory}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td>Net movement</td>
                <td>{formatCurrency(netMovement)}</td>
                <td colSpan={2}>{formatCurrency(totalIncomes)} + {formatCurrency(totalExpenses)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <div className="workspace-card import-card">
        <h3>Transaction import and review</h3>
        <p>Paste your CSV content, preview duplicates, then accept selected rows.</p>

        <textarea
          className="import-input"
          value={csvInput}
          onChange={(event) => setCsvInput(event.target.value)}
          rows={7}
        />

        <div className="import-actions">
          <button type="button" onClick={previewImport} disabled={isSubmitting}>
            Preview import
          </button>
          <button type="button" onClick={acceptSelected} disabled={isSubmitting || !preview}>
            Accept selected rows
          </button>
        </div>

        {importStatus && <p className="import-status">{importStatus}</p>}

        {preview && (
          <table>
            <thead>
              <tr>
                <th>Accept</th>
                <th>Date</th>
                <th>Description</th>
                <th>Amount</th>
                <th>Account</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {preview.transactions.map((transaction) => (
                <tr key={transaction.rowId} className={transaction.duplicate ? 'duplicate-row' : ''}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selectedRowIds.includes(transaction.rowId)}
                      onChange={() => toggleSelection(transaction.rowId)}
                    />
                  </td>
                  <td>{transaction.date}</td>
                  <td>{transaction.description}</td>
                  <td>{transaction.amount.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                  <td>{transaction.account}</td>
                  <td>{transaction.duplicate ? 'Duplicate' : 'New'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}

export default PlannerWorkspace;
