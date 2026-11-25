create table household_contracts (
    id varchar(36) primary key,
    owner_id varchar(36) not null,
    shared boolean not null,
    revision bigint not null check (revision > 0),
    payload clob not null
);

create index household_contracts_owner on household_contracts(owner_id);

create table contract_review_alerts (
    contract_id varchar(36) not null references household_contracts(id),
    term_id varchar(36) not null,
    recipient_id varchar(36) not null,
    timing varchar(40) not null,
    disposition varchar(12) not null check (disposition in ('UNREAD', 'READ', 'DISMISSED', 'SNOOZED')),
    snoozed_until date,
    primary key (contract_id, term_id, recipient_id),
    check ((disposition = 'SNOOZED') = (snoozed_until is not null))
);

create index contract_review_alerts_recipient on contract_review_alerts(recipient_id);
