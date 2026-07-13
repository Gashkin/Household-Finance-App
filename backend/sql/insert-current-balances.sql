-- H2 upsert script for the manually supplied current balances.
-- Run this against the local household-finance database.

MERGE INTO accounts (id, name, type, balance) KEY(id) VALUES ('AIB', 'AIB', 'Bank', 710.0);
MERGE INTO accounts (id, name, type, balance) KEY(id) VALUES ('Card', 'Card', 'Credit Card', -1725.0);