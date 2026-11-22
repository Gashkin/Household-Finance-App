---
type: API
title: Current Financial Month MCP
description: Minimal private read contract, online OAuth identity and one-use local approval for each snapshot.
resource: product/stories/current-financial-month-mcp.md
tags: [okf, api, mcp, privacy, cash-flow]
timestamp: 2026-11-22T08:00:00Z
---

# Current Financial Month MCP

The single `get_current_financial_month` tool reads the existing backend's saved budget period, owned ledger positions, dated commitments and account forecasts. Missing dates, stale evidence, unsupported sources and unverified alternatives remain explicit. No calendar-month fallback, generic query or financial mutation is supplied.

Online OAuth introspection and current local read permission are required on every financial invocation. A separate local owner approval is token/revision-bound, expires in two minutes and works once. Discovery contains no financial data. The dedicated loopback listener is separate from the general app API and is disabled on startup.

The backend snapshot is read-only, with optional forecast reads isolated so a rejected forecast cannot invalidate an otherwise safe snapshot. The workspace admission lock prevents edits/restore from interleaving. This is an application/transaction read boundary, not a separate OS or database principal.

- [Full contract](../../../product/stories/current-financial-month-mcp.md)
- [Setup, threat model and actual-owner acceptance](../../../ops/mcp-connection.md)
- [Service boundaries](../services/service-boundaries.md)
- [Schema compatibility](../runbooks/schema-compatibility.md)

The live Notion story owns scope/status. Real GBD-30 installation, supported-client OAuth/tunnel verification and the owner's real ChatGPT read/sign-off remain acceptance dependencies; automated synthetic tests do not replace them. Deferred AIB, installation and future per-agent entitlements remain separate stories.
