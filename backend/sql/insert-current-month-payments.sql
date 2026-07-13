-- H2 upsert script for the manually supplied current-month payments.
-- These are treated as one-off commitments in the current Jun26 month.
-- Run this against the local household-finance database.

MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-001', DATE '2026-06-01', 'Groceries', -560.0, 'Food & Groceries', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-002', DATE '2026-06-01', 'Vodafone', -155.0, 'Communications', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-003', DATE '2026-06-01', 'Allianz', -50.0, 'Insurance', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-004', DATE '2026-06-01', 'TV', -10.0, 'Utilities', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-005', DATE '2026-06-01', 'Netflix', -10.0, 'Subscriptions', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-006', DATE '2026-06-01', 'Ger', -220.0, 'Personal', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-007', DATE '2026-06-01', 'Laya', -10.0, 'Health', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-008', DATE '2026-06-01', 'Becky', -250.0, 'Family', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-009', DATE '2026-06-01', 'Extra', -190.0, 'Personal', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-010', DATE '2026-06-01', 'Disney', -10.0, 'Subscriptions', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-011', DATE '2026-06-01', 'elmwood', -35.0, 'Housing', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-012', DATE '2026-06-01', 'Dentist', -80.0, 'Health', 'ONE_OFF_FUTURE', NULL);
MERGE INTO transactions (id, txn_date, title, amount, category, txn_type, source_monthly_commitment_id) KEY(id) VALUES ('ONE-013', DATE '2026-06-01', 'Prop', -30.0, 'Housing', 'ONE_OFF_FUTURE', NULL);