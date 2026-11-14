import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import WorkspaceGate from './components/WorkspaceGate';
import { installWorkspaceTransport } from './api/workspace';
installWorkspaceTransport();
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <WorkspaceGate><App /></WorkspaceGate>
  </React.StrictMode>,
);
