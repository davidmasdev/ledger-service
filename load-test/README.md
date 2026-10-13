# Load test

A [k6](https://k6.io) script that measures how many `POST /transfers` per second the service sustains.
Results and method: [RESULTS.md](RESULTS.md).

## Prerequisites
- k6 installed natively: `winget install GrafanaLabs.k6`, or see the k6 docs for macOS/Linux.
  Don't run k6 from its Docker image against an app on the host. On Docker Desktop, the container-to-host network
  drops bursts of new connections (`dial: i/o timeout`), and those show up as errors that have nothing to do with the
  service.
- Docker running, for Postgres and Kafka.
- Port 8080 (the app) and 9092 (Kafka) free.

## Run
```bash
./mvnw spring-boot:run                          # terminal 1: app + Postgres + Kafka (compose.yaml)
k6 run -e RATE=500 load-test/transfers.js       # terminal 2
```

| Variable | Default | Meaning |
|---|---|---|
| `RATE` | `200` | Transfers started per second in the measured phase |
| `SCENARIO` | `spread` | `spread`: random pairs of accounts. `hot`: every transfer leaves the same account. |
| `ACCOUNTS` | `200` | Number of accounts the transfers are spread over |
| `BASE_URL` | `http://localhost:8080` | Where the app is |

Each run:
1. **Setup:** opens a funding account and `ACCOUNTS` protected accounts, and funds each with 1,000,000.00, so no
   transfer can fail for insufficient funds.
2. **Warm-up:** 30 s at `RATE`. Not counted.
3. **Measured phase:** 2 min at `RATE`, using an open model (`constant-arrival-rate`). k6 starts `RATE` transfers per
   second whether or not earlier ones have finished, so an overloaded service shows up as latency and dropped
   iterations instead of a quietly lower rate.

A run **sustains** `RATE` only if every threshold passes for the measured phase: 0% failed requests, p95 < 100 ms,
0 dropped iterations, and every response a 201. k6 exits with code 99 when a threshold fails.

## Finding the sustained rate
Raise `RATE` step by step until a run fails, then narrow down between the last pass and the first fail. Repeat the
best passing rate at least once. Runs on a shared machine vary, and a rate that passes only once isn't sustained.

## After a run
Check that the outbox caught up and how far behind it fell:

```bash
docker compose exec postgres psql -U ledger -d ledger -c \
  "select count(*) filter (where published_at is null) as pending,
          max(published_at - created_at) as max_publish_lag
   from outbox_events where created_at > now() - interval '5 minutes';"
```

Every run adds rows that are never deleted (the ledger is append-only). Remove the Compose volumes
(`docker compose down -v`) to start from an empty database.
