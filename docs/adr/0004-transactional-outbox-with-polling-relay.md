# 4. Transactional outbox with a polling relay

- Status: Approved
- Date: 2026-10-12

## Context

Other systems need to know about every journal entry, through Kafka. The entry lives in PostgreSQL, and there is no
transaction that spans both PostgreSQL and Kafka. Publishing directly from the service is wrong in either order:

- **Send first, then commit:** if the commit fails (an overdraft, a constraint, a crash), consumers have already seen
  an event for money that never moved.
- **Commit first, then send:** if the send fails or the process dies in between, the entry exists but the event is
  lost forever.

## Decision

**Write the event to the database in the same transaction as the entry, and publish it afterwards.**

- `JournalEntryService.save` is the single write path for entries, reversals and transfers. Right after saving the
  entry, it calls `OutboxWriter.append`, which inserts a `JournalEntryRecorded` event into `outbox_events`.
  `OutboxWriter` requires an existing transaction (`Propagation.MANDATORY`), so the event commits **if and only if**
  the entry does.
- `OutboxRelay.publishPending`, run every 500 ms by `OutboxRelayScheduler`, publishes one batch in its own
  transaction:
  1. `select … where published_at is null order by id limit 100 for update skip locked`
  2. send every event in the batch, keyed by journal entry id, with `eventId` (the outbox row id) and `eventType`
     headers
  3. wait for Kafka to acknowledge all of them (`acks=all`, idempotent producer, 10 s timeout)
  4. `update … set published_at = now()`
- Any failed or timed-out send throws `OutboxPublishException`. The transaction rolls back and the events stay pending
  for the next run.
- The payload is serialized once, when the event is written, and published exactly as stored.
  `JournalEntryRecorded` is the event contract, kept separate from the API response.

Each journal entry also gets a stored `kind` (`ENTRY`, `REVERSAL`, `TRANSFER`), so events and the API describe entries
the same way.

## Consequences

- **At-least-once delivery.** The relay can crash after Kafka acknowledged a send but before its transaction commits.
  The events are then sent again, so consumers must deduplicate on the `eventId` header. Events are never lost while
  the database survives.
- **Several app instances can relay at once.** `SKIP LOCKED` means each instance locks a different batch instead of
  waiting for the others, and no event is sent by two instances at the same time.
- **Ordering:** events are keyed by journal entry id, so Kafka orders events per entry, not per account or globally.
  Rebuilding balances doesn't need ordering, because addition doesn't depend on order. A consumer that needs
  per-account order would need a different key, which is hard here because one entry touches several accounts.
- **Latency:** up to the poll interval (500 ms), plus the send. Under a backlog, the scheduler keeps running batches
  without waiting.
- **The relay's transaction stays open while waiting for Kafka**, holding row locks and one connection for up to the
  send timeout. The batch size and timeout bound this, and the locks only cover outbox rows, never accounts or
  entries.
- **Published rows are kept.** The partial index `outbox_events_pending_idx` keeps the relay's query fast as the table
  grows. Deleting old published rows is future work.
- **Migration exception:** `V3` temporarily disables the append-only trigger on `journal_entries` to label existing
  reversals as `REVERSAL`. That is the deliberate, reviewable kind of exception ADR 0001 anticipated. A new check
  constraint then ties `kind = 'REVERSAL'` to `reversal_of is not null`.
- `OutboxRelayFailureTests` covers the failure path. It turns the scheduler off
  (`ledger.outbox.relay.enabled=false`) and drives the relay with a failing `KafkaTemplate`, which costs a second
  test context.

## Alternatives considered

- **Publish in an `@TransactionalEventListener(AFTER_COMMIT)`.** No new table, but the event is lost if the process
  dies or Kafka is down after the commit. That is the "commit first" problem above.
- **Kafka transactions chained with the database transaction.** Spring can coordinate the two, but they still commit
  one after the other (best effort, not atomic), so the gap between the commits remains.
- **Debezium change data capture (CDC) on the outbox table.** Lower latency and no polling, but it adds Kafka Connect
  and Debezium to run and operate. It also moves the delivery logic out of this codebase, which matters for a project
  where every line should be explainable.
- **Stored balances or snapshot events instead of per-entry events.** Out of scope. Per-entry events let consumers
  build any view they want.
