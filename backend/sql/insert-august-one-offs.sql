-- H2 upsert script for the manually supplied future one-off payments.
-- These are treated as August one-off commitments.
-- Run this against the local household-finance database.

MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('AUG-001', DATE '2026-08-01', 'Private App', -65.0, 'Subscriptions', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('AUG-002', DATE '2026-08-01', 'College Fees', -2900.0, 'Education', 'ONE_OFF_FUTURE', NULL);