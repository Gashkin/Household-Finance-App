# ITEM-004: Implement calculation engine and monthly summary data.

## Objective
Provide a calculation engine that derives monthly overview values and expose them to the consumer dashboard.

## Checklist
- [x] Add backend monthly summary calculation service.
- [x] Add backend endpoint for monthly overview report.
- [x] Add backend tests for monthly overview endpoint.
- [x] Add consumer dashboard component to display monthly summary.
- [x] Render recent transactions in the consumer dashboard.
- [ ] Replace sample transactions with persisted accepted imports (future item).

## Suggested files
- backend/src/main/java/com/householdfinance/service/MonthlySummaryService.java
- backend/src/main/java/com/householdfinance/controller/MonthlySummaryController.java
- backend/src/test/java/com/householdfinance/MonthlySummaryControllerTest.java
- frontend/src/components/ConsumerDashboard.tsx
- frontend/src/App.tsx
- frontend/src/styles.css
