# Evidence-backed cash position

As a household planner, I can build a dated cash position from named liquid accounts and unpaid commitments, inspect the calculation and use the available amount in a debt comparison without retyping it.

Acceptance: signed balances include overdrafts, no credit limits; commitments due from the position date through the chosen horizon are deducted exactly once; future/older evidence is rejected/warned respectively; overdue items are flagged and excluded; source labels and dates remain visible; backend owns totals; changes invalidate both position and dependent debt results. User explicitly transfers a valid nonnegative result. Values remain in memory only. No account connection or assumed future income.

Verification: service and real HTTP tests, frontend build, interaction verification. Separate follow-up story: export a reproducible debt comparison report, independently based on main.
