# 1. Enforce ledger invariants in the database, not only in the domain model

- Status: Approved
- Date: 2026-10-11

## Context

The ledger has invariants that must never be broken: postings and journal entries are append-only, a posting's
currency matches its account's currency, and an entry can be reversed at most once.

The application already enforces these. `JournalEntry` and `Posting` are Hibernate `@Immutable` with no setters, and
`JournalEntryService` checks account currencies and earlier reversals. But those checks only protect writes that go
through that code. Anything else that can reach the database bypasses them: a `psql` session, a future migration, a
`JdbcClient` query added later, or another service sharing the schema.

## Decision

The database enforces every invariant it can express cheaply, as a second line of defence (`V1__create_ledger_schema.sql`):

- **Append-only:** a `reject_modification()` trigger function raises an exception. It is attached `BEFORE UPDATE OR
  DELETE` (per row) and `BEFORE TRUNCATE` (per statement) on `postings` and `journal_entries`.
- **Currency match:** `accounts` has `unique (id, currency)`, and `postings` has
  `foreign key (account_id, currency) references accounts (id, currency)`. A posting in the wrong currency cannot exist.
- **Reverse at most once:** `journal_entries.reversal_of` is `unique`.
- **No empty postings:** `check (amount <> 0)`.

The service still checks these up front, because it gives better error messages (422 or 409 with a readable detail).
The constraints catch whatever the checks miss, including races: two concurrent reversals of the same entry both pass
`existsByReversalOf`, and the unique index rejects the second one. That rejection surfaces as a 409 through
`ApiExceptionHandler`.

`LedgerSchemaTests` checks these guarantees with plain SQL through `JdbcClient`, bypassing JPA on purpose.

**Not enforced in the database:** postings summing to zero per currency. That check lives only in
`JournalEntry.create`.

## Consequences

- Corrupting the ledger takes deliberate effort, such as a migration that drops a trigger. That makes it visible in
  code review.
- A mistaken entry can only be corrected with a reversal, never by editing it. This is the accounting rule we want.
- Test data cannot be cleaned up between tests. Tests isolate themselves with unique account names and assert only on
  data they created.
- An account's currency can effectively never change once it has postings, which is also what we want.
- The zero-sum rule depends on there being a single write path (`JournalEntry`). If another writer is ever added, it
  must be enforced in the database too (see below).

## Alternatives considered

- **Revoke `UPDATE`/`DELETE` from the application's database role.** A good complement, but it needs separate roles
  for migrating and for running the app. Today both use the same user. Worth doing before production.
- **A deferred constraint trigger that checks the zero-sum rule at commit.** Correct, but it adds a per-entry
  aggregate query to every commit. It is also harder to reason about, and there is only one write path today.
- **Trust the application only.** Simpler, but then the "never violate" invariants depend on every future line of
  code being correct.
