create table member_records (
    id varchar(80) primary key,
    kind varchar(16) not null,
    owner_id varchar(36) not null,
    version bigint not null,
    payload clob not null,
    updated_at timestamp(6) with time zone not null
);
create index member_records_kind_owner on member_records(kind, owner_id);
