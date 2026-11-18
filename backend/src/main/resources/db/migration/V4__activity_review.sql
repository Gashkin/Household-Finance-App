alter table ledger_entries add category_id varchar(36);
alter table ledger_entries add review_state varchar(12) default 'PROVISIONAL' not null;
alter table ledger_entries add linked_entry_id varchar(36);
alter table ledger_entries add link_type varchar(16);
create table ledger_review_records (
 id varchar(36) primary key, owner_id varchar(36) not null,
 kind varchar(16) not null, revision bigint not null, payload clob not null
);
