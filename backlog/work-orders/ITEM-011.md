# ITEM-011: Persist planner domain data and move planner service to repository-backed CRUD.

## Objective
Replace in-memory planner state with durable repository-backed reads/writes for accounts, categories, and commitments.

## Checklist
- [x] Add create, update, and delete operations in planner service for planner entities.
- [x] Wire planner controller endpoints to persisted CRUD flows.
- [x] Preserve startup seed behavior so initial UX remains usable in local dev.
- [x] Add/expand controller tests for persisted planner CRUD contract.
- [x] Validate backend tests for repository-backed planner operations.

## Suggested files
- backend/src/main/java/com/householdfinance/service/PlannerService.java
- backend/src/main/java/com/householdfinance/controller/PlannerController.java
- backend/src/main/java/com/householdfinance/entity/AccountEntity.java
- backend/src/main/java/com/householdfinance/entity/CategoryEntity.java
- backend/src/main/java/com/householdfinance/entity/CommitmentEntity.java
- backend/src/main/java/com/householdfinance/repository/AccountRepository.java
- backend/src/main/java/com/householdfinance/repository/CategoryRepository.java
- backend/src/main/java/com/householdfinance/repository/CommitmentRepository.java
- backend/src/test/java/com/householdfinance/PlannerControllerTest.java
