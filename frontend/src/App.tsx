import { useState } from 'react';
import ConsumerDashboard from './components/ConsumerDashboard';
import MigraterWorkspace from './components/MigraterWorkspace';
import PlannerWorkspace from './components/PlannerWorkspace';

function App() {
  const [role, setRole] = useState<'planner' | 'migrater' | 'consumer'>('planner');

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Household Finance</h1>
        <div className="role-switcher">
          <button onClick={() => setRole('planner')} className={role === 'planner' ? 'active' : ''}>
            Planner
          </button>
          <button onClick={() => setRole('migrater')} className={role === 'migrater' ? 'active' : ''}>
            Migrater
          </button>
          <button onClick={() => setRole('consumer')} className={role === 'consumer' ? 'active' : ''}>
            Consumer
          </button>
        </div>
      </header>

      <main className="app-main">
        {role === 'planner' ? <PlannerWorkspace /> : null}
        {role === 'migrater' ? <MigraterWorkspace /> : null}
        {role === 'consumer' ? <ConsumerDashboard /> : null}
      </main>
    </div>
  );
}

export default App;
