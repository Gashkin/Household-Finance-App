import DecisionPlansWorkspace from './components/DecisionPlansWorkspace';
import ActivityReviewWorkspace from './components/ActivityReviewWorkspace';
import BudgetWorkspace from './components/BudgetWorkspace';
import ForecastWorkspace from "./components/ForecastWorkspace";
import MoneyPositionWorkspace from './components/MoneyPositionWorkspace';
import WorkspaceRecovery from './components/WorkspaceRecovery';
import { useState } from 'react';
import DebtDecisionWorkspace from './components/DebtDecisionWorkspace';
import ConsumerDashboard from './components/ConsumerDashboard';
import MigraterWorkspace from './components/MigraterWorkspace';
import PlannerWorkspace from './components/PlannerWorkspace';
import HouseholdAccess from './components/HouseholdAccess';
import PermittedPosition from './components/PermittedPosition';
import MemberWorkspace from './components/MemberWorkspace';
import { useIdentity } from './api/access';

function App() {
  const identity=useIdentity();
  const read=identity?.canRead??false,edit=identity?.canEdit??false,custodian=identity?.custodian??false;
  const [role, setRole] = useState<'budget'|'review'|'member'|'access'|'decision' | 'planner' | 'migrater' | 'consumer' | 'recovery' | 'money' | 'forecast' | 'plans'>(!edit?(read?'money':'access'):location.pathname === '/bank/callback' ? 'money' : 'decision');

  const [planId, setPlanId] = useState<string | null>(null);

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Household Finance</h1>
        <nav className="role-switcher" aria-label="Workspaces">
          <button onClick={()=>setRole('access')} className={role==='access'?'active':''}>Household access</button>
          {read&&<button onClick={()=>setRole('member')} className={role==='member'?'active':''}>Spending &amp; purchases</button>}
          {edit&&<>
          <button onClick={()=>setRole('budget')} className={role==='budget'?'active':''}>Budgets &amp; allocations</button>
          <button onClick={()=>setRole('review')} className={role==='review'?'active':''}>Review &amp; reconcile</button>
          <button onClick={() => setRole('plans')} className={role === 'plans' ? 'active' : ''}>Financial plans</button>
          <button onClick={() => setRole('forecast')} className={role === 'forecast' ? 'active' : ''}>Plan ahead</button>
          <button onClick={() => setRole('money')} className={role === 'money' ? 'active' : ''}>Money position</button>
          <button onClick={() => setRole('decision')} className={role === 'decision' ? 'active' : ''}>Debt decision</button>
          </>}
          {!edit&&read&&<button onClick={()=>setRole('money')}>Money position</button>}
          {custodian&&edit&&<>
          <button onClick={() => setRole('planner')} className={role === 'planner' ? 'active' : ''}>
            Planner
          </button>
          <button onClick={() => setRole('migrater')} className={role === 'migrater' ? 'active' : ''}>
            Migrater
          </button>
          <button onClick={() => setRole('consumer')} className={role === 'consumer' ? 'active' : ''}>
            Consumer
          </button>
        </>}{custodian&&<button onClick={() => setRole('recovery')} className={role === 'recovery' ? 'active' : ''}>Backup &amp; recovery</button>}</nav>
      </header>

      <main className="app-main">
        {role==='budget'&&edit?<BudgetWorkspace/>:null}
        {role==='review'&&edit?<ActivityReviewWorkspace/>:null}
        {role==='access'?<HouseholdAccess/>:null}
        {role==='member'?<MemberWorkspace/>:null}
        {role === 'plans' ? <DecisionPlansWorkspace initialId={planId} /> : null}
        {role === 'forecast' ? <ForecastWorkspace /> : null}
        {role === 'money' ? edit?<MoneyPositionWorkspace openDecision={() => setRole('decision')} />:<PermittedPosition/> : null}
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
