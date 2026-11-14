import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const backend = loadEnv(mode, '.', '').WORKSPACE_BACKEND || 'http://127.0.0.1:8080';
  return {
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': backend, '/planner': backend, '/imports': backend, '/migration': backend, '/reports': backend, '/auth': backend },
  },
};
});
