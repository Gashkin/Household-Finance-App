-- Upgrade only an already-versioned workspace. No implicit adoption or shared credentials.
alter table ledger_accounts add column owner_id varchar(36) default 'owner' not null;
alter table ledger_accounts add column shared boolean default false not null;
alter table ledger_audit add column actor_id varchar(36) default 'owner' not null;
alter table cash_position_snapshots add column owner_id varchar(36) default 'owner' not null;
alter table cash_position_snapshots add column scope_refs varchar(40000);
alter table decision_plans add column owner_id varchar(36) default 'owner' not null;

create table household_users (
    id varchar(36) primary key,
    username varchar(40) not null unique,
    credential varchar(160),
    roles varchar(40) not null default '',
    state varchar(16) not null default 'INVITED',
    revision bigint not null default 0,
    invitation_hash varchar(64),
    invitation_expiry timestamp(6) with time zone,
    recovery_hash varchar(64)
);
create table access_audit (
    id varchar(36) primary key,
    actor_id varchar(36) not null,
    target_id varchar(36) not null,
    action varchar(32) not null,
    outcome varchar(32) not null,
    recorded_at timestamp(6) with time zone not null
);
