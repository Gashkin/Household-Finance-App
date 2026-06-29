## Primary Journey
A user reviews the household financial position, updates planned commitments, imports or records actual transactions, and sees the updated picture of cashflow, upcoming outgoings, and allowable spend.

Trigger:
- A need to understand current finances
- A new commitment or transaction needs to be recorded
- A monthly or regular review is due

What happens:
- The primary planner enters or imports transactions and commitments
- The system updates the current picture of balances and upcoming outgoings
- The planner compares planned vs actual activity
- The secondary consumer can view the simplified reporting view

End result:
- An up-to-date picture of current cashflow and what can safely be spent
- A plan that reflects the latest actual activity and known commitments

## All Journeys (prioritised)
1. Review household cashflow and allowable spend
2. Add or update recurring commitments
3. Add or update one-off commitments
4. Import or enter actual transactions
5. Reconcile planned vs actual and investigate deviations
6. Review the household financial picture at a high level

No other journeys come to mind right now.

## Failure Paths Per Top Journey
- A transaction is missed or not imported, and the system should make that visible rather than silently leaving the picture incomplete
- A large unexpected transaction arrives and changes the real picture, and the system should highlight the impact immediately
- The plan is too optimistic and does not reflect reality, and the system should surface the gap clearly rather than hiding it
- The simplified view is too complex and is not useful for the secondary consumer, and the system should offer a clearer high-level summary
- The data is not in sync, so the system appears to show more cash than is really available, and the system should prevent or flag that mismatch

## Automated Journeys
- Transaction import from bank accounts
- Updating derived reporting values such as balances, upcoming outgoings, and allowable spend
- Refreshing the household summary view after new data arrives

## Long-Running Journeys
Not really. Everything is effectively event-driven from either imported transactions or altered commitments.
