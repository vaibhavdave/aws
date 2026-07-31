# Module 07 — RDS (Users table + polyglot persistence)

## Theory

**RDS is managed relational hosting**, not a database engine of its own —
you pick an engine (Postgres, MySQL, MariaDB, SQL Server, Oracle), and AWS
handles the underlying instance, patching, and (if you ask for it) failover.

**Multi-AZ** runs a synchronously-replicated standby in a second AZ that RDS
fails over to automatically if the primary goes down — for availability, not
read scaling. **Read replicas** are the opposite: asynchronous, read-only
copies you can add to offload read traffic, in the same or other regions.
Neither is exercised by this module's single-instance setup, but the
distinction matters: reach for Multi-AZ when you need uptime, read replicas
when you need read throughput.

**Why this module is different from Modules 02–06:** S3, DynamoDB, SQS/SNS,
Lambda, and API Gateway are all emulated **in-process** by Floci. RDS is one
of the services Floci runs as a **real Docker container**
(`postgres:16-alpine` by default) — so this module isn't testing against a
mock of Postgres's behavior, it's talking to actual Postgres. That also means
first-time provisioning takes real time (pulling the image, initializing the
data directory), unlike the near-instant in-process services.

**Polyglot persistence:** this application now has two databases — the
`task-tracker` DynamoDB table (Module 03) and this module's Postgres `users`
table — because they suit different access patterns (flexible, high-throughput
key-value task storage vs. relational user records with uniqueness
constraints). There's no cross-database `JOIN`; `UserTaskSummaryService`
fetches from both repositories and combines the results in application code.
This is a deliberate, common real-world pattern — not a workaround.

## What you'll build

- **`RdsProvisioner`** — `CreateDBInstance` (idempotent), waits for
  `Available` via `RdsWaiter`, then reads back the assigned host/port from
  `DescribeDBInstances`.
- **`UserEntity` / `UserRepository`** — a standard Spring Data JPA entity +
  repository over the `users` table, created by the Flyway migration in
  `db/migration/V1__create_users_table.sql` (`spring.jpa.hibernate.ddl-auto`
  is `validate`, not `update` — the migration is the single source of truth
  for the schema, JPA just maps to it).
- **`UserTaskSummaryService`** — the polyglot join: given a username, reads
  the user from Postgres and that owner's tasks from DynamoDB
  (`TaskRepository`, reused from Module 03), and returns a combined summary.
- **`RdsUsersApplication`** — provisions RDS *before* Spring's context starts
  (the JDBC URL isn't known until the instance is available), then sets it as
  a system property so Spring's own datasource autoconfiguration picks it up
  with zero custom `DataSource` wiring.

## Running it

```bash
docker compose up -d
mvn -pl modules/07-rds spring-boot:run
```

First run pulls `postgres:16-alpine` and provisions the instance - expect it
to take noticeably longer than any previous module's first run.

```bash
curl -X POST http://localhost:8087/api/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com"}'

curl http://localhost:8087/api/users/alice/summary
```

## Tests

```bash
mvn -pl modules/07-rds test              # UserTaskSummaryServiceTest - both repos mocked
mvn -pl modules/07-rds test -Pfloci      # + RdsIntegrationTest against a real Postgres
```

`UserTaskSummaryServiceTest` verifies the join/summary logic with
`UserRepository` and `TaskRepository` both mocked - fast and deterministic.
`RdsIntegrationTest` provisions the real instance, runs the actual Flyway
migration, and does a plain JDBC insert/select round-trip - proving the
Docker-backed Postgres, not a mock of it, actually works. Like Modules 05/06,
it targets the `docker compose` Floci instance directly rather than an
ephemeral Testcontainers one, for a predictable route back to the container
RDS launches.

## Checkpoint

- Why is `spring.jpa.hibernate.ddl-auto` set to `validate` instead of
  `update` here?
- What would you lose (and gain) if `UserTaskSummaryService` were rewritten
  to periodically copy DynamoDB task counts into a column on the `users`
  table, instead of joining in application code on every request?
- Why does `RdsUsersApplication` provision the database *before* calling
  `SpringApplication.run()`, rather than inside a `@PostConstruct` or
  `ApplicationRunner`?

## Next

[Module 08 — ElastiCache](../08-elasticache) — add a caching layer in front
of the Task Tracker API's read path.
