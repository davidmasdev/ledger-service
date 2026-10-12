-- What kind of operation produced a journal entry, so the API and the published events agree on it.
alter table journal_entries
    add column kind varchar(10) not null default 'ENTRY'
        check (kind in ('ENTRY', 'REVERSAL', 'TRANSFER'));

-- Deliberate, one-off exception to append-only storage (ADR 0001): label the reversals that already exist. Nothing
-- else changes, and the trigger is re-enabled in the same transaction. Transfers made before this migration can't be
-- told apart from other entries and stay ENTRY.
alter table journal_entries disable trigger journal_entries_append_only;
update journal_entries set kind = 'REVERSAL' where reversal_of is not null;
alter table journal_entries enable trigger journal_entries_append_only;

-- From now on the application always sets the kind, and a reversal is exactly an entry that reverses another one.
alter table journal_entries alter column kind drop default;
alter table journal_entries
    add constraint journal_entries_reversal_kind check ((kind = 'REVERSAL') = (reversal_of is not null));

-- Transactional outbox: events are written in the same transaction as the journal entry they describe, then
-- published to Kafka by OutboxRelay. Published rows are kept; deleting old ones is future work.
create table outbox_events (
    id           bigint generated always as identity primary key,
    aggregate_id bigint       not null,
    event_type   varchar(100) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz
);

-- The relay only ever looks for pending events; this index stays small however many have been published.
create index outbox_events_pending_idx on outbox_events (id) where published_at is null;
