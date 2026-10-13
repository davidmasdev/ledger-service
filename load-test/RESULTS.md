# Load test results

**Headline: 500 transfers/s sustained**, with 0 errors and p95 latency of 19–32 ms. That's 60,000 transfers per
2-minute measured phase, and the result held in two separate runs. The 500/s was for the whole stack (app, Postgres,
Kafka and k6) on one laptop.

Measured on 2026-10-12 against the code as of Milestone 3 (commit `26ebb81`). Method and script:
[README.md](README.md), [transfers.js](transfers.js).

## Environment
| | |
|---|---|
| Machine | Laptop, Intel Core i7-11800H (8 cores / 16 threads), 32 GB RAM, Windows 11 Pro |
| App | Java 25.0.4, Spring Boot 4.1.1, default settings (Hikari pool of 10, outbox relay every 500 ms, batches of 100) |
| Infrastructure | PostgreSQL 17 and Kafka 4.1.0 (KRaft, single broker) in Docker Desktop 29.8.2, from `compose.yaml` |
| Load generator | k6 v2.2.0, native, on the **same machine** |

All four processes shared the same 16 threads. During a 650/s run, total CPU was 70–89%. The ceiling below is mostly
this machine's, not a single bottleneck in the code.

## `spread` scenario: transfers between 200 random accounts
Measured phase only (2 min). Pass means every threshold passed: 0% errors, p95 < 100 ms, 0 dropped iterations.

| Rate (/s) | Requests | Errors | p50 | p95 | p99 | Dropped | Result |
|---:|---:|---:|---:|---:|---:|---:|---|
| 400 | 48,000 | 0 | 8.2 ms | 11.1 ms | 13.6 ms | 0 | ✅ pass |
| 400 | 48,001 | 0 | 13.5 ms | 19.6 ms | 38.7 ms | 0 | ✅ pass (repeat, larger DB) |
| **500** | 60,001 | 0 | 12.5 ms | 31.8 ms | 79.4 ms | 0 | ✅ **pass** |
| **500** | 60,001 | 0 | 11.8 ms | 19.3 ms | 38.6 ms | 0 | ✅ **pass** (repeat) |
| 600 | 72,000 | 0 | 11.6 ms | 27.7 ms | 98.3 ms | 0 | ✅ pass |
| 600 | 71,618 | 0 | 63.7 ms | 525.6 ms | 728.6 ms | 383 | ❌ fail (repeat) |
| 650 | 77,625 | 1 | 103.4 ms | 451.4 ms | 694.5 ms | 375 | ❌ fail |
| 700 | 83,499 | 0 | 287.3 ms | 708.6 ms | 876.1 ms | 502 | ❌ fail |

600/s passed once and failed once, so it's above the sustained rate. 500/s is the highest rate that passed every time.

## `hot` scenario: every transfer debits the same account
| Offered rate (/s) | Completed in 2 min | Throughput | Errors | p95 | Dropped |
|---:|---:|---:|---:|---:|---:|
| 200 | 14,535 | **~121/s** | 0 | 2.21 s | 9,466 |
| 400 | 14,221 | **~119/s** | 0 | 4.12 s | 33,779 |

Throughput stays at about 120/s whatever rate is offered. This is the pessimistic lock from ADR 0003 working as
designed: debits of one account wait for each other's row lock, so each holds it for about 8 ms. There were no errors
and no overdraft. Extra load turns into queueing (latency and dropped iterations), not failures.

## Findings
1. **One account's debits are serialized by design.** The time the lock is held includes `SUM(postings)` over that
   account's history, which grows with every posting. That's the trade-off ADR 0003 anticipated, and the hot scenario
   measures it. A stored balance, updated under the same lock, would make that step constant-time. It's not done,
   because this milestone only measures.
2. **The outbox relay falls behind under load, then catches up.** In 500/s runs, events waited up to 17.3 s to be
   published, and up to 10.3 s at 600/s. They were all published (0 pending afterwards). The relay sends one batch of
   100 at a time and waits for Kafka's acknowledgement, so its throughput is limited by batch size and round trip.
   Larger batches or several relay threads would reduce the lag.
3. **Run-to-run variance near the limit is large** (the two 600/s runs). The database grew to 1.75 million postings
   during testing, autovacuum ran between runs, and the laptop was shared with everything else. That's why the
   headline is the rate that passed repeatedly.
4. **Don't run k6 in Docker against the host on Docker Desktop.** Earlier runs with the `grafana/k6` image had bursts
   of `dial: i/o timeout` (9–34 per run), even at 200/s, while the app logged no errors. The same rates run natively
   had 0 errors. Those Docker runs are excluded from the tables above.

## CV wording
> Sustained 500 transfers/s with 0 errors and p95 < 35 ms in k6 load tests (open-model, 2-minute runs) on a single
> 8-core laptop running the service, PostgreSQL and Kafka.
