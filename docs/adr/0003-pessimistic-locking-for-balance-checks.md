# 3. Pessimistic row locks for balance checks

- Status: Approved
- Date: 2026-10-12

## Context

Accounts with `allow_negative = false` must never go negative. Checking a balance and then posting is a
read-then-write sequence: if two transfers from the same account run at the same time, both can read a balance of
100.00, both decide 60.00 is affordable, and both post. That is double-spending.

Some accounts *must* be allowed to go negative. All balances in a currency sum to zero, so money enters the ledger from
an account like "external funding", which goes negative by exactly the amount it has put in.

## Decision

Every write goes through one path, `JournalEntryService.save`: posting an entry, reversing one, or making a transfer.
That path:

1. Works out each account's **net change** in the entry.
2. Locks only the accounts that both disallow negative balances **and** lose money in this entry, with
   `AccountRepository.lockForUpdate`. This is `@Lock(PESSIMISTIC_WRITE)` on a query ordered by id; with Hibernate on
   PostgreSQL it becomes `SELECT … ORDER BY id FOR NO KEY UPDATE`.
3. For each locked account, rejects the entry with 422 if `SUM(postings) + net change < 0`.
4. Saves the entry. The locks are released at commit.

Why this is correct at PostgreSQL's default `READ COMMITTED` isolation:

- Two debits of the same account queue on its row lock. The second runs its `SUM` only after it gets the lock, as a new
  statement. Under `READ COMMITTED` each statement sees everything committed before it started, so the second debit
  sees the first one's postings.
- Accounts that only receive money aren't locked. A concurrent credit can only raise a balance, so if the check misses
  one, it is merely stricter than it needs to be, never wrong.
- Locks are always taken in ascending id order. Two entries that debit the same set of accounts therefore queue
  instead of deadlocking.
- `FOR NO KEY UPDATE` doesn't conflict with the `FOR KEY SHARE` lock that PostgreSQL takes when a new posting
  references the account through its foreign key. So credits to a locked account aren't blocked.

The balance is still calculated (`SUM` over `postings`, indexed by `account_id`), never stored.

## Consequences

- `TransferConcurrencyTests` covers the core case: 20 concurrent transfers of 10.00 from an account holding 100.00
  result in exactly 10 successes, 10 rejections and a final balance of 0.00. `LockOrderingTests` runs entries that
  debit two accounts listed in opposite orders, and none of them deadlock.
- Debits of the same account are serialized, which limits throughput on a single hot account. Debits of different
  accounts don't block each other.
- A blocked transaction holds a database connection while it waits. Under heavy contention on one account, the
  connection pool (Hikari, 10 by default) is the first thing that runs out.
- `SUM(postings)` gets slower as an account's history grows. If load tests show this is the bottleneck, add a balance
  column updated under the same lock. The lock already serializes the writers, so it would stay consistent.

## Alternatives considered

- **Optimistic locking (`@Version` on the account, retry on conflict).** It holds no locks while working, but every
  concurrent debit of a hot account fails at commit and has to retry. Under contention that means wasted work and
  retry storms, and retry logic is easy to get subtly wrong. Pessimistic locks make contended requests queue instead
  of fail.
- **`SERIALIZABLE` isolation.** PostgreSQL would detect the conflict on its own, but it reports it as serialization
  failures that the application must retry, which is the same problem as optimistic locking. It also affects every
  transaction, not just debits.
- **Locking every account in the entry.** Simpler to explain, but it serializes every deposit through the funding
  account, the hottest row in the system, for no benefit.
- **A stored balance column with `CHECK (allow_negative OR balance >= 0)`.** This makes the database enforce the rule
  too, but it means storing the balance and updating it on every posting. That's a bigger change than this milestone
  needs. It's worth revisiting together with the balance-column optimization above.
