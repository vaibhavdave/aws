# Module 08 — ElastiCache (cache-aside Task reads)

## Theory

**ElastiCache is managed Redis/Memcached (or Valkey) hosting** — the same
value proposition as RDS, but for in-memory data stores instead of relational
ones: no patching, no cluster babysitting, and (on real AWS) Multi-AZ
failover and read replicas if you need them.

**Cache-aside** (a.k.a. lazy loading) is the pattern this module implements
explicitly, not through an annotation:

```
read:  check cache -> hit? return it.
       miss? read the source of truth -> populate cache -> return it.
write: update the source of truth -> invalidate (don't update) the cache entry.
```

Invalidating on write instead of updating the cached value is deliberate: it's
simpler and avoids a whole class of bugs where a partially-applied update
leaves the cache and source of truth disagreeing. The cost is that the very
next read after a write is guaranteed to be a cache miss — a tradeoff worth
naming, not an accident.

**TTLs** matter even with invalidation-on-write: they bound how stale a cache
entry can ever get if an invalidation is ever missed (a bug, a crash mid-write,
a bypassed code path), and they cap memory growth from keys nobody invalidates
because nothing ever explicitly deletes them. This module sets a 5-minute TTL
on every cached task.

## What you'll build

`CachedTaskService` wraps Module 03's `TaskRepository` with a Redis-backed
cache-aside layer using **Jedis** (specifically `UnifiedJedis`, its modern
synchronous client). `ElastiCacheProvisioner` creates a single-node Redis
replication group via `CreateReplicationGroup` (real AWS's `CreateCacheCluster`
only ever provisions memcached — Redis/Valkey always goes through
`CreateReplicationGroup`, even for a single node), waits for it, and reads back
the node's host/port from `DescribeReplicationGroups` — Floci runs this as a
real `valkey/valkey:8` container, so it's the actual Redis wire protocol
underneath, not a mock of it.

```
GET   /api/tasks/{id}   returns the task plus whether it was a cache hit and how long the lookup took
PATCH /api/tasks/{id}   updates status, invalidates that task's cache entry
```

`Lookup` (the result of `findById`) carries `cacheHit` and `elapsedNanos`
specifically so you can *see* the pattern working, not just trust it is.

## Running it

```bash
docker compose up -d
mvn -pl modules/08-elasticache spring-boot:run
```

```bash
# first call: cache miss (reads DynamoDB, populates the cache)
curl http://localhost:8088/api/tasks/<some-task-id>
# second call: cache hit
curl http://localhost:8088/api/tasks/<some-task-id>
```

A note on the benchmark: Floci's DynamoDB is in-process and already very
fast, so the local latency gap between a hit and a miss will look smaller
than it would against real, network-hop-away DynamoDB. The *mechanism* —
whether the DynamoDB call happens at all — is what to verify here, not the
absolute microsecond counts.

## Tests

```bash
mvn -pl modules/08-elasticache test              # CachedTaskServiceTest - both stores mocked
mvn -pl modules/08-elasticache test -Pfloci      # + real Redis-protocol behavior
```

`CachedTaskServiceTest` covers all four cache-aside branches (hit, miss +
populate, miss on a nonexistent task, invalidate-on-write) with `TaskRepository`
and `UnifiedJedis` both mocked. `ElastiCacheIntegrationTest` provisions the
real Docker-backed cache, saves a task, and proves the second read is a cache
hit and a post-update read is a fresh miss with the new status — against the
`docker compose` Floci instance directly, same reasoning as Modules 05-07.

## Checkpoint

- Why does `updateStatus` call `redis.del(...)` instead of
  `redis.setex(...)` with the new value?
- What's the concrete failure mode a TTL protects against, given that every
  write path already invalidates its own key?
- If two concurrent requests both miss the cache for the same task, what
  happens? Is that a problem here?

## Next

[Module 09 — ECS / EC2](../09-ecs-ec2) — package the Task Tracker API as a
container and run it as a managed service instead of a Lambda function.
