create table accounts (
    id         bigint generated always as identity primary key,
    name       varchar(200) not null unique,
    currency   varchar(3)   not null check (currency ~ '^[A-Z]{3}$'),
    created_at timestamptz  not null,
    -- Target of the composite foreign key on postings.
    unique (id, currency)
);

create table journal_entries (
    id          bigint generated always as identity primary key,
    description varchar(500) not null,
    -- Unique: an entry can be reversed at most once.
    reversal_of bigint unique references journal_entries (id),
    created_at  timestamptz  not null
);

create table postings (
    id               bigint generated always as identity primary key,
    journal_entry_id bigint        not null references journal_entries (id),
    account_id       bigint        not null,
    currency         varchar(3)    not null,
    amount           numeric(19, 4) not null check (amount <> 0),
    -- A posting's currency must match its account's currency.
    foreign key (account_id, currency) references accounts (id, currency)
);

create index postings_account_id_idx on postings (account_id);
create index postings_journal_entry_id_idx on postings (journal_entry_id);

-- Postings and journal entries are append-only; corrections are reversal entries.
create function reject_modification() returns trigger
    language plpgsql as
$$
begin
    raise exception '% is append-only: % is not allowed', tg_table_name, tg_op;
end;
$$;

create trigger postings_append_only
    before update or delete on postings
    for each row execute function reject_modification();

create trigger postings_no_truncate
    before truncate on postings
    for each statement execute function reject_modification();

create trigger journal_entries_append_only
    before update or delete on journal_entries
    for each row execute function reject_modification();

create trigger journal_entries_no_truncate
    before truncate on journal_entries
    for each statement execute function reject_modification();

create table idempotency_keys (
    key              varchar(255) primary key,
    -- SHA-256 hex of the operation and request, to detect a key reused for a different request.
    request_hash     varchar(64)  not null,
    journal_entry_id bigint references journal_entries (id),
    created_at       timestamptz  not null default now()
);
