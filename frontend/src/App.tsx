import { useEffect, useRef, useState, type MouseEvent } from 'react';
import {
  useIdentity,
  type WorkspaceModule,
  type WorkspacePage,
} from './api/access';
import ActivityReviewWorkspace from './components/ActivityReviewWorkspace';
import BudgetWorkspace from './components/BudgetWorkspace';
import ConsumerDashboard from './components/ConsumerDashboard';
import DebtDecisionWorkspace from './components/DebtDecisionWorkspace';
import DebtStatementsWorkspace from './components/DebtStatementsWorkspace';
import DecisionPlansWorkspace from './components/DecisionPlansWorkspace';
import GoalsWorkspace from './components/GoalsWorkspace';
import HouseholdAccess from './components/HouseholdAccess';
import McpConnection from './components/McpConnection';
import MemberWorkspace from './components/MemberWorkspace';
import MigraterWorkspace from './components/MigraterWorkspace';
import MoneyPositionWorkspace from './components/MoneyPositionWorkspace';
import PermittedPosition from './components/PermittedPosition';
import PlanAheadWorkspace from './components/PlanAheadWorkspace';
import PlannerWorkspace from './components/PlannerWorkspace';
import WorkspaceRecovery from './components/WorkspaceRecovery';

function currentRoute() {
  return {
    path: location.pathname,
    planId: typeof history.state?.planId === 'string' ? history.state.planId : null,
  };
}

function App() {
  const identity = useIdentity();
  const modules = identity?.modules ?? [];
  const defaultPage = modules[0]?.pages[0];
  const [route, setRoute] = useState(currentRoute);
  const main = useRef<HTMLElement>(null);
  const focusOnChange = useRef(false);

  const path = route.path === '/'
    ? defaultPage?.path
    : route.path === '/bank/callback'
      ? modules.find(module => module.id === 'overview')?.pages[0]?.path
      : route.path;
  const activeModule = modules.find(module => module.pages.some(page => page.path === path));
  const activePage = activeModule?.pages.find(page => page.path === path);

  useEffect(() => {
    function restoreRoute() {
      focusOnChange.current = true;
      setRoute(currentRoute());
    }
    window.addEventListener('popstate', restoreRoute);
    return () => window.removeEventListener('popstate', restoreRoute);
  }, []);

  useEffect(() => {
    document.title = activePage ? activePage.label + ' | Household Finance' : 'Household Finance';
    if (focusOnChange.current) {
      main.current?.focus({ preventScroll: true });
      window.scrollTo(0, 0);
      focusOnChange.current = false;
    }
    return () => { document.title = 'Household Finance'; };
  }, [route, activePage]);

  function open(page: WorkspacePage, planId: string | null = null) {
    if (!main.current || location.pathname !== route.path) return;
    if (route.path === page.path && route.planId === planId) return;
    history.pushState({ planId }, '', page.path);
    focusOnChange.current = true;
    setRoute(currentRoute());
  }

  function openItem(id: string, planId?: string) {
    const page = modules.flatMap(module => module.pages).find(item => item.id === id);
    if (page) open(page, planId);
  }

  function follow(event: MouseEvent<HTMLAnchorElement>, page: WorkspacePage) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (page.path !== path) open(page);
  }

  function moduleLink(module: WorkspaceModule) {
    const current = module.id === activeModule?.id;
    const page = current && activePage ? activePage : module.pages[0];
    return (
      <li key={module.id}>
        <a href={page.path} aria-current={current ? 'location' : undefined}
          onClick={event => follow(event, page)}>{module.label}</a>
      </li>
    );
  }

  function renderWorkspace() {
    switch (activePage?.id) {
      case 'money':
        return identity?.canEdit
          ? <MoneyPositionWorkspace openDecision={() => openItem('decision')} />
          : <PermittedPosition />;
      case 'member': return <MemberWorkspace />;
      case 'review': return <ActivityReviewWorkspace />;
      case 'forecast': return <PlanAheadWorkspace />;
      case 'budget': return <BudgetWorkspace />;
      case 'goals': return <GoalsWorkspace openBills={() => openItem('forecast')} />;
      case 'plans': return <DecisionPlansWorkspace initialId={route.planId} />;
      case 'debts':
        return <DebtStatementsWorkspace openMoney={() => openItem('money')}
          openReview={() => openItem('review')} openForecast={() => openItem('forecast')} />;
      case 'decision': return <DebtDecisionWorkspace onPlanCreated={id => openItem('plans', id)} />;
      case 'access': return <HouseholdAccess />;
      case 'recovery': return <WorkspaceRecovery />;
      case 'mcp': return <McpConnection />;
      case 'planner': return <PlannerWorkspace />;
      case 'migrater': return <MigraterWorkspace />;
      case 'consumer':
        return <ConsumerDashboard onOpenMoney={() => openItem('money')} onOpenForecast={() => openItem('forecast')} />;
      default:
        return (
          <section>
            <h2>Workspace unavailable</h2>
            <p>This page is unavailable for your current access. Choose a workspace from the navigation.</p>
            {defaultPage && <a href={defaultPage.path} onClick={event => follow(event, defaultPage)}>
              Go to {defaultPage.label}
            </a>}
          </section>
        );
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Household Finance</h1>
        <a className="skip-workspace-navigation" href="#workspace-content">Skip to workspace content</a>
        <nav aria-label="Modules" className="module-navigation">
          <ul>{modules.map(moduleLink)}</ul>
        </nav>
        {activeModule && (
          <nav aria-label={activeModule.label + ' pages'} className="module-pages">
            <ul>
              {activeModule.pages.map(page => (
                <li key={page.id}>
                  <a href={page.path} aria-current={page.id === activePage?.id ? 'page' : undefined}
                    onClick={event => follow(event, page)}>{page.label}</a>
                </li>
              ))}
            </ul>
          </nav>
        )}
      </header>
      <main className="app-main" id="workspace-content" tabIndex={-1} ref={main}>
        {renderWorkspace()}
      </main>
    </div>
  );
}

export default App;
