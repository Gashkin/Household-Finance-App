import DecisionPlansWorkspace from './components/DecisionPlansWorkspace';
import ForecastWorkspace from "./components/ForecastWorkspace";
import MoneyPositionWorkspace from './components/MoneyPositionWorkspace';
import WorkspaceRecovery from './components/WorkspaceRecovery';
import { useState } from 'react';
import DebtDecisionWorkspace from './components/DebtDecisionWorkspace';
import ConsumerDashboard from './components/ConsumerDashboard';
import MigraterWorkspace from './components/MigraterWorkspace';
import PlannerWorkspace from './components/PlannerWorkspace';

function App() {
  const [role, setRole] = useState<'decision' | 'planner' | 'migrater' | 'consumer' | 'recovery' | 'money' | 'forecast' | 'plans'>(location.pathname === '/bank/callback' ? 'money' : 'decision');

  const [planId, setPlanId] = useState<string | null>(null);

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Household Finance</h1>
        <nav className="role-switcher" aria-label="Workspaces">
          <button onClick={() => setRole('plans')} className={role === 'plans' ? 'active' : ''}>Financial plans</button>
          <button onClick={() => setRole('forecast')} className={role === 'forecast' ? 'active' : ''}>Plan ahead</button>
          <button onClick={() => setRole('money')} className={role === 'money' ? 'active' : ''}>Money position</button>
          <button onClick={() => setRole('decision')} className={role === 'decision' ? 'active' : ''}>Debt decision</button>
          <button onClick={() => setRole('planner')} className={role === 'planner' ? 'active' : ''}>
            Planner
          </button>
          <button onClick={() => setRole('migrater')} className={role === 'migrater' ? 'active' : ''}>
            Migrater
          </button>
          <button onClick={() => setRole('consumer')} className={role === 'consumer' ? 'active' : ''}>
            Consumer
          </button>
        <button onClick={() => setRole('recovery')} className={role === 'recovery' ? 'active' : ''}>Backup &amp; recovery</button></nav>
      </header>

      <main className="app-main">
        {role === 'plans' ? <DecisionPlansWorkspace initialId={planId} /> : null}
        {role === 'forecast' ? <ForecastWorkspace /> : null}
        {role === 'money' ? <MoneyPositionWorkspace openDecision={() => setRole('decision')} /> : null}
        {role === 'recovery' ? <WorkspaceRecovery /> : null}
        {role === 'decision' ? <DebtDecisionWorkspace onPlanCreated={id => { setPlanId(id); setRole('plans'); }} /> : null}
        {role === 'planner' ? <PlannerWorkspace /> : null}
        {role === 'migrater' ? <MigraterWorkspace /> : null}
        {role === 'consumer' ? <ConsumerDashboard onOpenMoney={() => setRole('money')} onOpenForecast={() => setRole('forecast')} /> : null}
      </main>
    </div>
  );
}

export default App;
