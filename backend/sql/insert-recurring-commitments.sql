-- H2 upsert script for the manually supplied recurring monthly commitments.
-- These rows are marked as recurring so the planner can generate them forward.
-- Run this against the local household-finance database.

MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-001', 'Salary', 6560.0, 1, 'Income');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-002', 'Groceries', -1350.0, 1, 'Food & Groceries');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-003', 'Mortgage', -1670.0, 1, 'Housing');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-004', 'CU', -580.0, 1, 'Savings');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-005', 'Dog Food', -100.0, 1, 'Pets');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-006', 'Vodafone', -165.0, 1, 'Communications');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-007', 'Allianz', -50.0, 1, 'Insurance');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-008', 'Car Insurance', -100.0, 1, 'Insurance');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-009', 'Bin', -35.0, 1, 'Utilities');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-010', 'TV', -10.0, 1, 'Utilities');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-011', 'Netlix', -10.0, 1, 'Subscriptions');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-012', 'Littlewoods', -65.0, 1, 'Shopping');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-013', 'Ger', -450.0, 1, 'Personal');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-014', 'Utility Bill', -125.0, 1, 'Utilities');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-015', 'Laya', -10.0, 1, 'Health');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-016', 'Life Insurance', -75.0, 1, 'Insurance');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-017', 'Disney Plus', -10.0, 1, 'Subscriptions');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-018', 'Medicine', -80.0, 1, 'Health');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-019', 'Becky', -500.0, 1, 'Family');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-020', 'extra', -500.0, 1, 'Personal');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-021', 'Clothes &House', -400.0, 1, 'Clothing & Home');
MERGE INTO monthly_commitments (id, title, amount, day_of_month, category) KEY(id) VALUES ('REC-022', 'Tax', -55.0, 1, 'Tax');