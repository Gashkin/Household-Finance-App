import { useEffect, useState } from 'react';

interface MonthlyTransaction {
  date: string;
  description: string;
  amount: number;
  account: string;
}

interface MonthlyOverview {
  month: string;
  startingBalance: number;
  actualInflow: number;
  actualOutflow: number;
  committedOutgoings: number;
  projectedEndBalance: number;
  allowableSpend: number;
  recentTransactions: MonthlyTransaction[];
}

function formatCurrency(value: number) {
  return value.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
}

function ConsumerDashboard() {
  const [overview, setOverview] = useState<MonthlyOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadMonthlyOverview = async () => {
    setLoading(true);
    setError('');

    try {
      const response = await fetch('http://localhost:8080/reports/monthly-overview');
      if (!response.ok) {
        throw new Error('Request failed.');
      }

      const data = (await response.json()) as MonthlyOverview;
      setOverview(data);
    } catch {
      setError('Unable to load monthly overview. Ensure backend is running on port 8080.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadMonthlyOverview();
  }, []);

  const cashflowNet = overview ? overview.actualInflow - overview.actualOutflow : 0;
  const cashflowMagnitude = overview ? Math.max(overview.actualInflow, overview.actualOutflow, 1) : 1;
  const cashflowRatio = Math.min(100, Math.round((Math.abs(cashflowNet) / cashflowMagnitude) * 100));
  const healthState = cashflowNet >= 0 ? 'On Track' : 'Needs Attention';

  return (
    <section>
      <div className="dashboard-header">
        <div>
          <h2>Consumer dashboard</h2>
          <p>Monthly overview of financial position and allowable spend.</p>
        </div>
        <button type="button" onClick={() => void loadMonthlyOverview()} disabled={loading}>
          Refresh summary
        </button>
      </div>

      {loading && <p>Loading monthly summary...</p>}
      {error && <p className="dashboard-error">{error}</p>}

      {overview && !loading && (
        <>
          <div className="workspace-card cashflow-card">
            <h3>Cashflow health</h3>
            <p className={cashflowNet >= 0 ? 'cashflow-positive' : 'cashflow-negative'}>
              {healthState}: {formatCurrency(cashflowNet)}
            </p>
            <div className="cashflow-meter" aria-label="cashflow health meter">
              <div className="cashflow-fill" style={{ width: `${cashflowRatio}%` }} />
            </div>
          </div>

          <div className="summary-grid">
            <div className="summary-card">
              <h3>Month</h3>
              <p>{overview.month}</p>
            </div>
            <div className="summary-card">
              <h3>Starting balance</h3>
              <p>{formatCurrency(overview.startingBalance)}</p>
            </div>
            <div className="summary-card">
              <h3>Actual inflow</h3>
              <p>{formatCurrency(overview.actualInflow)}</p>
            </div>
            <div className="summary-card">
              <h3>Actual outflow</h3>
              <p>{formatCurrency(overview.actualOutflow)}</p>
            </div>
            <div className="summary-card">
              <h3>Committed outgoings</h3>
              <p>{formatCurrency(overview.committedOutgoings)}</p>
            </div>
            <div className="summary-card highlight">
              <h3>Allowable spend</h3>
              <p>{formatCurrency(overview.allowableSpend)}</p>
            </div>
          </div>

          <div className="workspace-card transactions-card">
            <h3>Recent transactions</h3>
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Account</th>
                  <th>Amount</th>
                </tr>
              </thead>
              <tbody>
                {overview.recentTransactions.map((transaction) => (
                  <tr key={`${transaction.date}-${transaction.description}-${transaction.amount}`}>
                    <td>{transaction.date}</td>
                    <td>{transaction.description}</td>
                    <td>{transaction.account}</td>
                    <td className={transaction.amount >= 0 ? 'cashflow-positive' : 'cashflow-negative'}>
                      {formatCurrency(transaction.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

export default ConsumerDashboard;
