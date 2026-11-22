# GBD-33 — private current-month ChatGPT connection

This is an opt-in, read-only MCP interface. It is **not enabled on startup**, even after a restart with valid configuration. It does not connect banks, install the local Windows application, initiate payments or implement per-agent entitlements. GBD-30 owns local installation and protected storage; GBD-34 owns deferred AIB connectivity; GBD-35 owns future agent entitlements. Live [Product Build](https://www.notion.so/418d3419fef081f79b7bfeeed488eb86) and [GBD-33](https://www.notion.so/41fd3419fef0810f8d86fb75173f17fc) remain authoritative.

## Supported connection and prerequisites

Use the owner's ChatGPT custom MCP connection, **OAuth**, and **Secure MCP Tunnel** to the dedicated `http://127.0.0.1:8787/mcp` listener. Do not tunnel port 8080, the database, local controls or a generic API proxy. Do not open inbound firewall ports or change `server.address`. Tunnel setup and OAuth issuer provisioning require the owner's account permissions; neither is silently provisioned by this code.

Official guidance checked on 22 November 2026:

- [Connecting custom MCP servers](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt): custom server access depends on plan/workspace permissions; connection configuration supports HTTPS or Secure MCP Tunnel and OAuth.
- [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels): outbound HTTPS forwarding, tunnel roles, runtime credentials, private HTTP MCP targets, OAuth discovery and outage behaviour. The OAuth authorization server must remain independently reachable.
- [Authentication](https://developers.openai.com/plugins/build/auth): standard OAuth discovery, scoped bearer tokens and authorization challenges.
- [App permissions](https://help.openai.com/en/articles/11487775-connected-apps-in-chatgpt): Always ask is an available permission option; availability and agent controls vary by surface. This implementation does **not** claim that a native dialog has been demonstrated on the owner's account.

The server additionally enforces a local, single-use approval for every snapshot read. Native permission settings cannot bypass this backend control. Choose Always ask in ChatGPT where available. No standing permission is accepted by the backend. The first tool call requests approval and returns **no financial data**; the owner approves its exact ID in the local app, and a second invocation consumes that approval to retrieve one snapshot. New reads, denied requests and retries after consumption require new approvals.

## OAuth issuer configuration

Use a trusted OAuth/OIDC authorization server supporting authorization code with PKCE (S256), the exact ChatGPT callback shown during setup, refresh rotation/revocation, resource/audience restriction and RFC 7662 **online introspection**. Do not use a password grant, a hand-shared API key, a model-supplied identity or a token hardcoded in the repository. Configure the issuer's ChatGPT client and resource introspection client through its administrative console. A provider that cannot return the following token contract is unsupported until an adapter is implemented and verified; do not weaken validation to connect it.

Every financial tool call introspects its bearer token over TLS. Required active-token response:

| Field | Required value |
| --- | --- |
| `active` | JSON boolean `true` |
| `token_type` | `Bearer`, identifying an access token |
| `iss` | Exact configured issuer |
| `sub` | Exact configured owner subject |
| `aud` | Configured resource audience, or an array containing it |
| `scope` | `finance.current-month.read`; only optional `openid` and `offline_access` additionally permitted |
| `exp` | Integer seconds, strictly future and at most 600 seconds away |
| `nbf` | If present, an integer not in the future |

The issuer must issue access tokens with a maximum ten-minute lifetime and make introspection return inactive immediately on revocation. The resource rejects refresh tokens. No introspection result or financial snapshot is cached. A scope/issuer/audience mismatch, inaccessible issuer, inactive token, disabled local user or changed local permission revision fails closed. The configured local user must already exist and retain read access. The MCP actor is reduced to READER, with no editor, administrator or custodian capability.

Pass these settings to the backend through the GBD-30 protected launch/secret mechanism (or a process-only secret-manager injection for synthetic development). Keep secrets out of command history, files checked into Git, Notion, chat and screenshots. Do not use persistent plaintext user environment settings as a secret vault.

| Setting | Purpose |
| --- | --- |
| `FINANCE_MCP_INTROSPECTION` | Exact HTTPS introspection endpoint; redirects prohibited |
| `FINANCE_MCP_ISSUER` | Exact HTTPS issuer advertised through protected-resource discovery |
| `FINANCE_MCP_AUDIENCE` | Stable HTTPS resource identifier ending in `/mcp`, consistent with the connector/tunnel resource and issuer audience |
| `FINANCE_MCP_CLIENT_ID` | Issuer-approved resource introspection client ID |
| `FINANCE_MCP_CLIENT_SECRET` | Introspection client credential, minimum 16 characters; never sent to the model |
| `FINANCE_MCP_SUBJECT` | Owner's exact issuer subject; not an email/name supplied by an agent |
| `FINANCE_MCP_USER_ID` | Existing local user ID; default `owner` |
| `FINANCE_MCP_PORT` | Dedicated loopback port; default 8787 |

If any required setting is missing or not TLS, the listener does not start. A valid configuration starts a **disabled** listener; authenticate in the local app as the financial owner, open **ChatGPT connection**, and select **Enable connection**. Setup does not change financial records or other users' entitlements.

## Tunnel and client setup

1. On the secure running GBD-30 installation, configure the issuer and protected backend settings. Start the app and confirm the normal local interface still works.
2. In Platform tunnel settings, create a private tunnel for the intended ChatGPT workspace. The owner needs the documented Tunnels Manage/Use roles and a narrowly scoped runtime key. Use the current official `tunnel-client` onboarding; verify its Windows support and version on this host before installing it. Store its identity/key separately from the protected financial data directory.
3. Configure its private HTTP target as `http://127.0.0.1:8787/mcp`. Ensure local forwarding uses the loopback Host header and preserves Authorization. OAuth protected-resource discovery at `/.well-known/oauth-protected-resource` must reach the same dedicated listener. Run `tunnel-client doctor --profile <profile> --explain`. Do not enable raw request/response logging.
4. Add the custom MCP server in ChatGPT using the tunnel, choose OAuth, complete the issuer's browser login/consent, and verify the only discovered tool is `get_current_financial_month`. Use the exact callback/client configuration shown by the supported client. ChatGPT manages its connection credentials; do not paste access tokens into a conversation.
5. Set native **Always ask** where enforceable. Ask for current-month balances and dated commitments. Confirm the native prompt if shown. In Finance App > ChatGPT connection, refresh requests, compare the exact request ID with ChatGPT, and approve **this one read**. Return to ChatGPT and allow its single retry with that approval ID.
6. Repeat the prompt, deny it, and confirm no snapshot is retrieved. Repeat within the same chat and a supported autonomous/Work context. Verify different tool names/alternate endpoints cannot bypass local approval. A native-dialog test and the real owner read must be recorded separately from automated harness tests.

An unavailable tunnel, issuer, supported Windows client or workspace permission is an acceptance blocker, not a reason to use an unauthenticated tunnel. This PR does not claim those owner-account steps have been performed.

## Tool contract and cash-flow interpretation

See [the contract](../product/stories/current-financial-month-mcp.md). It returns the **saved current budget period**, using Europe/Dublin consistently with the backend; end is exclusive. Missing/ambiguous period returns `PERIOD_UNAVAILABLE`, never a calendar-month fallback. Currency is EUR and money uses integer cents. This version does not support selecting arbitrary dates, accounts, households, files, SQL, URLs, historical reports or mutation operations.

The result is field-allowlisted. Account display names are generated aliases; raw account names/numbers, payees, narratives, statement files, arbitrary source strings and authentication material are excluded. Stable IDs remain opaque references. Due date and actual settlement date are distinct. Already-posted events identify incorporation into the current ledger anchor. A matched commitment replaces its posted cash event, and reciprocal transfers/settlements are one movement with account linkage. Pending reserves do not become a second future debit. Debt statement expectations come from the debt backend, not MCP arithmetic.

Saved manual-plan events have unknown funding/matching and are labelled unverified alternatives, **not** additional outstanding obligations. Undated published reserves and budget allocations appear as exceptions with `cashFlow=false`; do not assign them an invented date or add them to dated commitments. Missing review timestamps and unsupported debt principal/interest splits are explicit coverage limitations. Forecasts are calculated by the existing commitment backend and remain illustrative where its evidence is incomplete. ChatGPT can plot supplied data, but must not label its own sums an app-verified forecast.

## Trust boundary, revocation and recovery

The tunnel terminates/forwards sensitive application data through OpenAI; OpenAI can process the bounded payload. This is not end-to-end encryption against the recipient. The trusted identity issuer handles OAuth credentials/tokens and sees introspection requests, not financial responses. Assess their account policies, retention and consent before using real household records. Disable raw tunnel/HTTP tracing and ensure no intermediary stores/replays financial responses after revocation. Only the narrow MCP listener is forwarded; local controls and the general API are not exposed.

The listener binds IPv4 loopback, validates Host, rejects browser Origin and cookie headers, accepts only bounded JSON MCP POSTs, and exposes only discovery plus one allowlisted read. It cannot proxy other app endpoints. Limits: 4 KiB requests, 1 MiB responses, 50 accounts, 20,000 source rows, 1,000 dated output events, at most 35 period days, 30 listener requests/minute, 20 pending/decided approvals and bounded worker/queue sizes. Limits fail closed; no truncated snapshot is presented as complete. Introspection has a five-second total deadline and 32 KiB response cap.

Each approval is bound to the exact access token hash and local permission revision, expires after two minutes, and is consumed atomically once before financial retrieval. Denial, expiry, replay, disable or restart cannot confer standing permission. A stolen live token cannot redeem another token's approval or grant itself local approval; its residual risk is creating confusing approval requests and using an approval the owner mistakenly grants for that exact token/request. Compare request IDs and approve only expected calls. Revoke the token at the issuer, disable MCP locally, and stop/remove the tunnel if compromise is suspected.

**Disable and revoke approvals** takes effect through the same workspace write/read admission boundary as financial writes. It leaves local Finance App use intact. In-flight reads finish before disable completes; subsequent reads are denied. Restart disables the connector and discards approvals. Rotate the introspection credential in protected configuration and restart; reconnect ChatGPT through OAuth. Lost connections never return a cached financial snapshot. No bank operation or record mutation is part of reconnecting.

Minimal audits contain timestamp, pseudonymous caller, fixed scope/tool, outcome/denial code and generated request ID. They exclude tokens, subject identifiers, request arguments and financial payloads. The local UI retains the latest 100 events in memory; structured application logs retain the same minimal events under the operator's existing protected log retention policy. Local approval state and its history intentionally reset on restart.

The read path has an application READER principal and a read-only transaction. It shares the backend process/database connection pool; it is **not a separate database role or OS sandbox**. The tunnel identity must have no access to the protected database/secrets outside this backend route; enforce this with GBD-30's verified Windows identity/ACL provisioning. Local administrator/SYSTEM and arbitrary code running as the finance process user remain outside that confidentiality boundary. No claim of protected real-data operation is made without that installation evidence.

## Required owner acceptance evidence

On the actual GBD-30 installation, record sanitised versions/configuration facts, OAuth/tunnel success, expiry/revocation/outage and disable behaviour, native Always ask behaviour on repeat/Work calls, denied approvals causing no retrieval, and the owner's real ChatGPT tool invocation. Ask ChatGPT to plot the current period cash-flow map and compare sample balances, due/payment dates and amounts with the installed app. Synthetic records are acceptable for the first exercise. Do not publish tokens, account details or raw transcripts. The story stays open until owner sign-off and human-controlled PR integration; a local harness cannot replace them.
