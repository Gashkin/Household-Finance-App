import { useEffect, useState } from 'react';

interface MonthlyOverview {
  month: string;
  status: 'UNAVAILABLE';
  explanation: string;
}

function ConsumerDashboard({ onOpenMoney, onOpenForecast }: {
  onOpenMoney: () => void;
  onOpenForecast: () => void;
}) {
  const [overview, setOverview] = useState<MonthlyOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadMonthlyOverview = async () => {
    setLoading(true);
    setOverview(null);
    setError('');
    try {
      const response = await fetch('/reports/monthly-overview', { cache: 'no-store' });
      if (!response.ok) throw new Error('Summary unavailable');
      const data = await response.json();
      if (data.status !== 'UNAVAILABLE' || typeof data.explanation !== 'string') {
        throw new Error('Unsupported summary evidence');
      }
      setOverview(data);
    } catch {
      setError('Unable to load the monthly summary. Unlock the workspace and try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void loadMonthlyOverview(); }, []);

  return (
    <section>
      <div className="dashboard-header">
        <h2>Consumer dashboard</h2>
        <button type="button" onClick={() => void loadMonthlyOverview()} disabled={loading}>
          Refresh summary
        </button>
      </div>
      {loading && <p>Loading monthly summary...</p>}
      {error && <p role="alert" className="dashboard-error">{error}</p>}
      {overview && !loading && (
        <div className="workspace-card">
          <h3>No verified monthly summary</h3>
          <p>{overview.explanation}</p>
          <p>Period: {overview.month}. Missing evidence is not a zero balance or permission to spend.</p>
          <button type="button" onClick={onOpenMoney}>View reviewed money position</button>
          <button type="button" onClick={onOpenForecast}>Open dated affordability plan</button>
        </div>
      )}
    </section>
  );
}

export default ConsumerDashboard;
