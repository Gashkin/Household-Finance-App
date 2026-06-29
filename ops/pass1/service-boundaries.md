# Service Boundaries

## Core services
- Planner workspace service: manages commitments, categories, accounts, and transaction review.
- Consumer reporting service: serves the monthly overview for the secondary consumer.
- Import service: handles file intake, normalization, deduplication, and review.
- Calculation service: produces balances, forecasts, allowable spend, and reconciliation results.

## Ownership boundaries
- The planner service owns editable financial data.
- The consumer service owns read-only reporting views.
- The import service owns imported transaction processing.
- The calculation service owns derived financial metrics.
