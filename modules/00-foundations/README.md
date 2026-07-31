# Module 00 — Foundations & Environment

## Theory

**What "the cloud" actually is.** AWS rents you compute, storage, networking and
managed services over an API instead of you buying hardware. Three service models
matter for orientation:

- **IaaS** (EC2, EBS) — you get raw virtual machines/disks, you manage the OS up.
- **PaaS** (Elastic Beanstalk, RDS) — AWS manages the runtime/engine, you manage your app/data.
- **SaaS** (e.g. AWS-hosted apps) — you just use the product.

Everything in this curriculum sits somewhere on that spectrum: S3 and DynamoDB are
fully-managed (closer to SaaS for storage), Lambda is "serverless PaaS", EC2/ECS
are IaaS/CaaS.

**Global infrastructure.** AWS is organized as:

- **Regions** — independent geographic areas (e.g. `us-east-1`). Nothing replicates
  across regions unless you explicitly configure it.
- **Availability Zones (AZs)** — isolated data centers within a region, used for
  fault tolerance (e.g. Multi-AZ RDS).
- **Edge locations** — CloudFront/Route53 points of presence, closer to end users.

Every AWS SDK client is constructed with a `Region`. In real AWS that determines
which physical infrastructure your request hits and what data residency applies.
Floci accepts any region string as a label with no physical meaning — but you'll
still specify one, because production code always must.

**Shared responsibility model.** AWS secures "of the cloud" (physical hosts,
hypervisor, managed-service internals). You secure "in the cloud" (IAM policies,
data encryption choices, network configuration, your application code). Module 01
(IAM) is where this becomes concrete.

**Why learn on an emulator instead of a real AWS account?** A real account means
a credit card, the risk of a surprise bill from a misconfigured resource, and
network latency on every single API call while you're still learning the shape
of the SDK. [Floci](https://floci.io) removes all three: it's free, it can't bill
you because it isn't real infrastructure, and it answers requests in milliseconds
on your own machine. The code you write against it is the *same* AWS SDK v2 code
that would run against production — only the endpoint and credentials change — so
none of what you learn is emulator-specific trivia.

**How Floci works.** It's a single ~40MB native binary (built with Quarkus/GraalVM)
that speaks the real AWS wire protocol on `http://localhost:4566`. Services split
into three tiers:

- **In-process** (S3, DynamoDB, SQS, SNS, IAM, STS, KMS, and most others) — fast,
  pure emulation, no Docker required for these specifically.
- **Real Docker containers** (Lambda, RDS, ElastiCache, ECS, EC2, EKS, MSK, ...) —
  Floci launches actual `postgres`, `valkey`, Lambda runtime, etc. containers via
  the Docker socket, so behavior fidelity is much higher than a mock.
- **Storage modes** — `memory` (fastest, nothing survives a restart), `hybrid`
  (in-memory + async flush every 5s — what this repo's `docker-compose.yml` uses),
  `persistent`, and `wal` (write-ahead log, strongest durability).

Because Docker-backed services need to launch sibling containers, Floci itself
needs the Docker socket mounted — see `docker-compose.yml` at the repo root.

## Setup

From the repo root:

```bash
docker compose up -d
docker compose logs -f floci   # wait for it to report ready, then Ctrl-C
```

Floci is now listening on `http://localhost:4566` with account id `000000000000`
(the default Floci falls back to when you use non-numeric credentials like `test`).

## Hands-on: Hello Cloud

This module ships two things:

1. `FlociEndpoint` (`src/main/java/.../FlociEndpoint.java`) — a small reusable
   helper that every later module imports. It takes any AWS SDK v2 client
   builder and applies the endpoint override, region, and dummy credentials:

   ```java
   FlociEndpoint floci = FlociEndpoint.local();
   StsClient sts = floci.configure(StsClient.builder());
   ```

2. `HelloCloud` (`src/main/java/.../HelloCloud.java`) — calls STS's
   `GetCallerIdentity`, the same call real AWS tooling uses to answer
   "who am I, and what account is this?"

Run it:

```bash
mvn -pl modules/00-foundations compile exec:java
```

Expected output looks like:

```
Connected to Floci at http://localhost:4566
Account : 000000000000
ARN     : arn:aws:sts::000000000000:assumed-role/... (or similar)
UserId  : ...
```

In real AWS, the **account ID** scopes every resource ARN you'll ever see
(`arn:aws:s3:::my-bucket` has no account, but
`arn:aws:dynamodb:us-east-1:123456789012:table/my-table` does). The **ARN**
format itself — `arn:partition:service:region:account-id:resource` — is worth
memorizing now; you'll read dozens of them from here on.

## Tests

```bash
mvn -pl modules/00-foundations test              # FlociEndpointTest - no Docker needed
mvn -pl modules/00-foundations test -Pfloci      # + HelloCloudIntegrationTest, starts a real Floci via Testcontainers
```

`FlociEndpointTest` is a plain unit test verifying the builder logic in isolation.
`HelloCloudIntegrationTest` is tagged `@Tag("floci")` and uses Floci's own
Testcontainers module (`io.floci:testcontainers-floci`) to spin up a disposable
Floci instance per test class — no manual `docker compose up` needed for this one,
Testcontainers manages the container lifecycle itself. This unit-vs-integration
split (fast tests always on, Docker-dependent tests opt-in via the `floci` Maven
profile) is used in every module in this curriculum.

## Checkpoint

Before moving on, make sure you can answer:

- What's the difference between a region and an availability zone?
- Under the shared responsibility model, who is responsible for IAM policy
  correctness — AWS, or you?
- Why does `FlociEndpoint.configure()` work for `StsClient`, `S3Client`, and
  `DynamoDbClient` alike, unmodified?

## Next

[Module 01 — IAM & Security](../01-iam) — provision the roles and policies that
every later module's demo will run under.
