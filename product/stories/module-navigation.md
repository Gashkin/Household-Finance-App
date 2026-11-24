# Workspace navigation (GBD-38)

Sign in to the local Finance App and choose a module, then a page. Opening the app at `/` takes a financial user to **Overview → Money position**. A user with no financial role starts at **Settings → Household access**. The current page has a bookmarkable link under `/workspaces/`. Refresh and browser Back/Forward keep the selected page. Use Tab and Enter to follow the links, or use **Skip to workspace content** to bypass navigation.

| Module | Pages | Page access |
| --- | --- | --- |
| Overview | Money position | Reader or Editor. Readers see the shared-only position. Editors use the full ledger workspace within their own and shared account scope. |
| Activity | Spending & purchases, Review & reconcile | Reader or Editor for spending and purchases. Editor for the review workspace. |
| Planning | Plan ahead, Budgets & allocations, Goals & irregular costs, Financial plans | Editor |
| Debt | Cards, loans & mortgages, Debt decision | Editor |
| Settings | Household access, Backup & recovery, ChatGPT connection, Planner, Migrater, Consumer | Household access for every signed-in user. Admin for people and access events. Custodian for backup and ChatGPT connection. Custodian with Editor for the three legacy pages. |

Module links appear when there is an accessible page. A bookmark to an unavailable page shows a generic access message and links to permitted modules, including after signing in with a different account. The bank consent callback continues to open Money position, and existing financial API URLs are retained.

## Upgrading

The server derives module grants per session from the existing roles and fixed owner/custodian identity. Upgrading requires no role changes, database migration, or backup conversion. Admin combines with financial roles without adding financial access on its own. Role or sharing changes retain the existing session revocation behaviour, and sign-in recalculates navigation from current grants.

The backend registry maps workspace modules to HTTP capabilities and sends the permitted page list through `/api/access/me`. The public workspace paths return the sign-in shell; only a page granted by that session is mounted. Financial API writes generally require Editor. Existing scoped Reader API reads and read-only calculations remain available even where the corresponding editing workspace is hidden. Readers can still manage their personal spending decisions. Domain checks continue to enforce per-account ownership, explicit sharing, access to saved evidence, and special rules for member activity. Bank connections and temporary evidence intake still require custodian plus Editor. The existing password confirmation requirement applies to sensitive operations.

When adding a page, register its stable ID, label and grant in `WorkspaceModules`, map its API operations to the corresponding module, and register the component in `App.tsx`. Exercise the route and API with an allowed role and a denied role, including any ownership restrictions. Unmapped endpoint families are rejected by the workspace filter.

## Verification

Run the backend and browser suites using the Java 21 and Node setup in the [README](../../README.md). `HouseholdAccessTest` covers all eight role combinations, workspace shell routes, API admission and revocation. `module-navigation.spec.ts` covers keyboard navigation, bookmarks, refresh, Back/Forward, mixed roles, and direct access after revocation in desktop and mobile Chromium.
