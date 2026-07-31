# Module 03 — DynamoDB (Task Tracker data layer)

## Theory

**DynamoDB is a key-value/document store, not a relational database.** There's
no schema for non-key attributes, no joins, and no `SELECT * WHERE arbitrary
column = x` without either a secondary index or a full table scan. Design
starts from your access patterns, not from normalized entities — this is the
single biggest mental shift coming from SQL.

**Keys:**

- **Partition key** (a.k.a. hash key) — determines which physical partition an
  item lives on. Every `GetItem`/`PutItem` needs it.
- **Sort key** (a.k.a. range key), optional — lets multiple items share a
  partition key, ordered by this key. Not used on this module's base table
  (`id` alone is enough to address a task), but used on the GSI below.

**Secondary indexes** let you query by something other than the base table's
key. A **GSI** (Global Secondary Index) has its own partition/sort key and can
span all partitions — this module's `owner-index` GSI has partition key
`owner` and sort key `createdAt`, so "give me alice's tasks, newest first" is
an efficient `Query`, not a table scan. (An LSI — Local Secondary Index —
shares the base table's partition key and only adds an alternate sort key;
not needed here.)

**Capacity modes:** `PAY_PER_REQUEST` (on-demand, billed per request, zero
capacity planning — what this module uses) vs. `PROVISIONED` (you set
read/write capacity units yourself, cheaper at steady high volume but you can
get throttled if you undersize it).

**DynamoDB Streams** captures an ordered, time-limited (24h) log of every
item-level change (insert/modify/remove), each with the item's old and/or new
image depending on the `StreamViewType`. This module enables
`NEW_AND_OLD_IMAGES` and reads it with the raw
describe-stream → get-shard-iterator → get-records protocol so you see the
mechanics; Module 05 replaces that manual polling with a Lambda event source
mapping, which does exactly this for you.

## What you'll build

A clean split between:

- **`Task`** — the domain model your application code works with (a record,
  `TaskStatus` enum, `Instant` timestamps).
- **`TaskItem`** — the `@DynamoDbBean` persistence bean the Enhanced Client
  actually maps to table attributes (string-typed, matches what a
  `Query`/`Scan` returns).
- **`TaskMapper`** — pure conversions both ways, plus rebuilding a `Task`
  straight from a Streams record's raw `AttributeValue` map.
- **`TaskTableAdmin`** — schema/DDL on the low-level `DynamoDbClient`: creates
  the table, the `owner-index` GSI, and turns on Streams.
- **`TaskRepository`** — CRUD on the Enhanced Client (`DynamoDbEnhancedClient`
  / `DynamoDbTable<TaskItem>`): `save`, `findById`, `findByOwner` (GSI query),
  `updateStatus`, `delete`.
  `TaskStreamPoller` — reads new inserts straight off the DynamoDB Stream.

Run the walkthrough:

```bash
docker compose up -d
mvn -pl modules/03-dynamodb compile exec:java
```

It creates the table, saves three tasks across two owners, looks one up by
id, queries the GSI for one owner's tasks, updates a status, then reads the
insert events back off the stream.

## Tests

```bash
mvn -pl modules/03-dynamodb test              # TaskMapperTest - pure, no Floci
mvn -pl modules/03-dynamodb test -Pfloci      # + repository CRUD/GSI and Streams integration tests
```

- `TaskMapperTest` — round-trips `Task -> TaskItem -> Task`, and rebuilds a
  `Task` from a hand-built Streams image, all without touching DynamoDB.
- `TaskRepositoryIntegrationTest` — save/find/update/delete against a real
  Floci-backed table, and proves `findByOwner` only returns that owner's
  tasks via the GSI.
- `TaskStreamIntegrationTest` — inserts a task, then polls the stream (using
  Awaitility to tolerate the async flush of Floci's `hybrid` storage mode)
  until that insert shows up.

## Checkpoint

- Why does `owner-index` need `createdAt` as a sort key, not just `owner` as
  the whole index?
- What would go wrong if you tried to query "all tasks with status = DONE"
  without an index on `status`?
- Why is `TaskMapper` a separate class instead of putting `toDomain()`/`toItem()`
  methods directly on `Task` and `TaskItem`?

## Next

[Module 04 — SQS & SNS](../04-sqs-sns) — publish an event whenever a task's
status changes, and fan it out to independent consumers.
