# ITEM-001: Set up the application shell and authentication skeleton

## Objective
Create a minimal end-to-end skeleton for the household finance app with:
- a frontend shell with planner and consumer views
- a backend health/auth endpoint
- a simple role-based placeholder for planner and consumer access

## Files to create
- backend/pom.xml
- backend/src/main/java/com/householdfinance/HouseholdFinanceApplication.java
- backend/src/main/java/com/householdfinance/controller/HealthController.java
- backend/src/main/java/com/householdfinance/controller/AuthController.java
- backend/src/main/java/com/householdfinance/service/AuthContextService.java
- backend/src/test/java/com/householdfinance/HealthControllerTest.java
- backend/src/test/java/com/householdfinance/AuthControllerTest.java
- frontend/package.json
- frontend/tsconfig.json
- frontend/tsconfig.node.json
- frontend/vite.config.ts
- frontend/index.html
- frontend/src/main.tsx
- frontend/src/App.tsx
- frontend/src/styles.css

## Acceptance criteria
- The backend exposes a health endpoint and an auth endpoint that returns the current role.
- The frontend renders a shell with planner and consumer navigation and a role toggle.
- The backend and frontend build successfully.
- Tests cover the backend health and auth behavior.
