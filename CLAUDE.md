# ledger-service

Double-entry ledger REST service. Portfolio project: correctness and clarity matter more than feature count.
Every line must be explainable in an interview.

## Stack
- Java 25, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator), Maven wrapper
- PostgreSQL 17, schema managed only by Flyway (`src/main/resources/db/migration`), JPA runs with `ddl-auto: validate`
- Tests: JUnit 5, Testcontainers (Docker must be running)

## Commands
- Build and run all tests: `./mvnw verify`
- Single test class: `./mvnw test -Dtest=ClassName`
- Run locally: `./mvnw spring-boot:run` (starts Postgres from `compose.yaml` automatically)
- Run with Testcontainers instead: `./mvnw spring-boot:test-run`

## Domain invariants (never violate)
- Every journal entry has >= 2 postings, and its postings sum to exactly zero **per currency**.
- Postings and journal entries are append-only: never UPDATE or DELETE them. Corrections are reversal entries.
- Money is `BigDecimal` (or `long` minor units) with an explicit currency. Never `double`/`float`.
- A posting's currency must match its account's currency.
- When locking several accounts, lock them in ascending id order (deadlock avoidance).
- Writes that move money accept an `Idempotency-Key`; a replay returns the original response.

## Conventions
- Package by feature under `io.github.davidmasdev.ledgerservice` (e.g. `account`, `journal`), not by layer.
- Controllers stay thin; transactions (`@Transactional`) live in the service layer.
- API errors use RFC 9457 Problem Details.
- Schema changes = a new Flyway migration `V<n>__<description>.sql`. Never edit an applied migration.
- Prefer records for DTOs; do not expose JPA entities from controllers.

## Guardrails (automatic, see `.claude/settings.json`)
- Every edited `.java` file is formatted by Spotless (palantir-java-format) right after the edit. Don't fight the formatter.
- `./mvnw verify` (tests + `spotless:check`) runs when a turn ends with changes under `src/` or `pom.xml`; a red build must be fixed before finishing.
- CI (`.github/workflows/ci.yml`) runs the same `verify` on every push and PR.

## Workflow
- Plan before non-trivial changes; wait for approval.
- Write or update tests first, then implement.
- Definition of done: `./mvnw verify` green, migration added if schema changed, invariants above covered by tests.
- Non-obvious design decisions go in `docs/adr/` (the author writes these).
