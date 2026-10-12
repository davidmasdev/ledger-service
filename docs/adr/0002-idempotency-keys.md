# 2. Idempotency keys: insert the key first, rebuild the response on replay

- Status: Approved
- Date: 2026-10-11

## Context

Clients retry money-moving requests after timeouts and network errors. A retry must never post a second journal entry,
and it must get back the same response as the original request (CLAUDE.md invariant). Retries can also arrive *while*
the original is still running, so the mechanism has to be safe under concurrency, not only for sequential replays.

## Decision

`POST /journal-entries` and `POST /journal-entries/{id}/reversal` require an `Idempotency-Key` header (non-blank, at
most 255 characters). `IdempotencyService.execute` runs inside the caller's transaction
(`Propagation.MANDATORY`):

1. `insert into idempotency_keys (key, request_hash) ... on conflict (key) do nothing`.
2. **If the row was inserted,** run the operation, then store the new `journal_entry_id` on the key row.
3. **If it conflicted,** read the stored row. If its `request_hash` differs from ours, return 422 (the key was reused
   for a different request). Otherwise return the stored entry.

`request_hash` is a SHA-256 of a normalized description of the request (`JournalEntryService.fingerprint`). It covers
the operation, the description and each posting, with amounts normalized via
`stripTrailingZeros().toPlainString()`. That way a retry sending `10` instead of `10.00` is recognized as the same
request. For a reversal, the description is the operation plus the entry id from the path.

**Concurrency comes from PostgreSQL, not application code.** While the first transaction holds an uncommitted row for
the key, a second insert with the same key blocks on the primary-key index:

- If the first transaction **commits**, the second insert does nothing and the request takes the replay path. Under
  `READ COMMITTED`, its next `select` sees the committed row.
- If the first transaction **rolls back**, the second insert succeeds and that request runs the operation itself.

So a failed request (such as an unbalanced entry) never uses up its key, and the client can fix the request and retry
with the same key. `IdempotencyConcurrencyTests` sends 8 simultaneous requests with one key and checks that only one
entry was posted and all 8 responses are equal. It uses fewer requests than Hikari's 10 connections, because each
waiting request holds a connection.

**The response isn't stored, it's rebuilt.** Journal entries are immutable, so building the response again from the
stored entry gives the original response. For that to hold byte for byte:

- Amounts are formatted with exactly the currency's minor-unit digits (`Money.format`), so `10.00` going in and the
  stored `10.0000` render the same.
- `createdAt` is truncated to microseconds, PostgreSQL's precision, before the first response is built.
- A replay answers `201 Created` with the same `Location` header.

## Consequences

- No locks, queues or extra infrastructure. The guarantee is one unique index plus the transaction boundary.
- A duplicate request that arrives while the original is still in flight waits until the original's transaction ends.
  This is bounded by the transaction's duration, which is short.
- The fingerprint lists every request field explicitly. A new field added to the request must also be added to
  `fingerprint`, or two requests that differ only in that field will count as the same request.
- A key row always has its `journal_entry_id` when it's read, because both are written in one transaction. If that
  ever stops being true, the replay throws instead of answering "entry 0 not found"
  (`IdempotencyServiceTests`).
- Keys are global. Once authentication exists, they must be scoped per client, for example with a primary key of
  `(client_id, key)`.
- Keys never expire, so the table grows forever. A retention job (such as deleting keys older than 24 hours) is future
  work.
- This only works for operations whose result is a journal entry. An operation with a different result would need its
  own reference column, or would need to store the response.

## Alternatives considered

- **Store the full response body (status, headers, JSON).** Needed when responses can't be rebuilt. Here it would only
  duplicate immutable data and add a JSON column.
- **Check the key with a `select`, then insert it.** This has a race: two requests both see no row and both post.
- **Do the work first and rely on the unique constraint to reject duplicates.** It wastes the work, and it aborts the
  PostgreSQL transaction, so the replay has to be served from a new one.
- **A distributed lock (such as Redis).** Extra infrastructure for a guarantee the database already gives us.
