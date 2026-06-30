# Error Handling

## Rules
- Validation errors during import must be surfaced to the user before acceptance.
- Calculation errors must fail safely and present a clear message.
- Access violations must be blocked at the service boundary.
- Data import conflicts must be marked for review rather than silently resolved.
