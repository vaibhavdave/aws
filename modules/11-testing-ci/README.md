# Module 11 — Testing, CI & Observability

## Theory

**The testing pyramid this curriculum has actually been building, module by
module:**

- **Pure unit tests** (no Floci, no network) — `TaskMapperTest` (03),
  `MultipartUploaderTest` (02), `TaskEventCodecTest` (04),
  `UserTaskSummaryServiceTest` (07), `CachedTaskServiceTest` (08),
  `StructuredLoggerTest` (this module). Fast, deterministic, run on every
  `mvn test`.
- **HTTP-layer tests with the service mocked** — `FileVaultControllerTest` (02),
  `TaskApiRouterTest` (06), `TaskControllerTest` (09). Verify routing and
  request/response mapping without touching AWS at all.
- **Integration tests against a real Floci** — every module's `@Tag("floci")`
  test. Two flavors, chosen deliberately per module:
  - **Testcontainers-managed** (`FlociContainer`, spun up per test class) —
    Modules 00-04 and this one, where the thing under test only needs to
    reach Floci's HTTP API at `:4566`.
  - **`docker compose`-managed** (targeting `localhost:4566` directly) —
    Modules 05-09, where a *Docker-backed* resource Floci launches (a Lambda
    container, an RDS/ElastiCache container, an ECS task) needs a
    predictable route back to Floci over the Docker network, which an
    ephemeral Testcontainers instance can't guarantee.
- **CDK synthesis assertions** — Module 10's `TaskTrackerStackIT`, which
  needs neither Docker nor Floci at all, since CDK's `Template` API
  synthesizes entirely in-process.

**Why this split matters for CI:** a CI pipeline that only ever runs
`mvn test` gets fast, useful signal but never exercises a single line of
actual AWS-shaped behavior. `mvn verify -Pfloci` is what turns every one of
those `@Tag("floci")` tests on — and *that's* what proves the curriculum's
code works against something AWS-API-shaped, not just against itself.

**Structured logging** means printing JSON lines (not free-text) to
stdout/stderr. In real Lambda, ECS, or EC2, that's the entire mechanism —
the platform's CloudWatch agent captures stdout automatically and ships it
to CloudWatch Logs; your code never calls `PutLogEvents` itself. That's why
`StructuredLogger` in this module just serializes and prints — it's what you'd
actually write inside a handler. `CloudWatchLogShipper` (which *does* call
`PutLogEvents`/`FilterLogEvents` directly) exists only to make that automatic
platform behavior visible and queryable locally, so you can see what a
CloudWatch Logs Insights-style query (`filterPattern`) does with the
structured fields you logged.

## What you'll build

- **`StructuredLogEvent` / `StructuredLogger`** — JSON log lines with
  `timestamp`/`level`/`component`/`message`/`fields`, printed to stdout.
- **`CloudWatchLogShipper`** — ships a batch of those events into a Floci
  CloudWatch Logs group/stream, then queries them back with a filter
  pattern - proving the structure survives the round trip and is actually
  queryable, not just readable. Real AWS's JSON filter-pattern syntax
  (`{ $.level = "ERROR" }`) isn't parsed by Floci, which matches
  `filterPattern` as a plain substring of the message instead, so the test
  filters on a substring (`"level":"ERROR"`) that only the error event's
  JSON contains.
- **`.github/workflows/ci.yml`** (repo root) — the CI pipeline: unit tests +
  packaging first (fast signal, and it's what produces every module's jars),
  then builds Module 09's container image, starts Floci, waits for it, and
  runs the full `-Pfloci` suite across every module.

## Running it

```bash
docker compose up -d
mvn -pl modules/11-testing-ci compile exec:java
```

To run the whole curriculum's tests the way CI does, from the repo root:

```bash
mvn clean verify                                              # fast: every module's unit tests + Module 10's CDK IT
docker build -t task-tracker-container:latest modules/09-ecs-ec2
docker compose up -d
mvn verify -Pfloci                                             # full: every module's Floci-tagged integration test too
```

## Tests

```bash
mvn -pl modules/11-testing-ci test              # StructuredLoggerTest - pure
mvn -pl modules/11-testing-ci test -Pfloci      # + CloudWatchLogShipperIntegrationTest
```

## Checkpoint

- Why does `StructuredLogger` never call a CloudWatch Logs API directly?
- Of this curriculum's three integration-test styles (Testcontainers-managed,
  docker-compose-managed, CDK-synthesis-only), which would you pick for a
  new module that deploys an EventBridge rule triggering an SQS queue, and
  why?
- What would `mvn test` (without `-Pfloci`) in CI actually have caught, and
  what would it have missed, if Module 04's SNS filter policy wiring had a bug?

## Next

The [Capstone](../../capstone-cloudmart) ties every module together into one
serverless mini e-commerce platform.
