# Learning AWS with Java, on Floci

A hands-on curriculum for learning AWS by writing real Java code against
[Floci](https://floci.io) — a free, open-source, local AWS emulator — instead of a
billed AWS account. Floci speaks the real AWS wire protocol on `http://localhost:4566`,
so every client built here (`software.amazon.awssdk...`) would work unmodified against
real AWS; only the endpoint and credentials differ.

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 21+ | All modules target Java 21 |
| Maven | 3.9+ | Multi-module reactor build |
| Docker / Docker Compose | recent | Runs Floci itself, and the services Floci backs with real containers (Lambda, RDS, ElastiCache, ECS) |

Verify:

```bash
java -version
mvn -version
docker --version
```

## Running Floci

```bash
docker compose up -d      # starts Floci on localhost:4566
docker compose logs -f    # watch it come up (~ms to a few seconds)
docker compose down       # stop it when you're done
```

No AWS account, no credentials, no auth token — the modules use dummy credentials
(`test`/`test`) that Floci accepts unconditionally.

## How each module works

Every folder under `modules/` is a Maven submodule with the same shape:

```
modules/NN-topic/
  README.md        <- theory + what you'll build + how to run the demo
  src/main/java/... <- the demo code
  src/test/java/... <- tests
```

Tests come in two flavors:

- **Plain unit tests** — run on every `mvn test`, no Floci required.
- **Integration tests tagged `@Tag("floci")`** — talk to a real Floci instance.
  They're excluded by default so the build stays fast without Docker running.
  Start Floci first, then run them with:

  ```bash
  mvn test -Pfloci
  ```

Build everything (unit tests only):

```bash
mvn clean install
```

Build one module directly:

```bash
mvn -pl modules/00-foundations -am clean test
```

## Curriculum

| # | Module | AWS services | You'll build |
|---|---|---|---|
| 00 | [Foundations & Environment](modules/00-foundations) | STS | "Hello Cloud" connectivity check |
| 01 | [IAM & Security](modules/01-iam) | IAM, STS | Programmatic role/policy provisioning + assume-role |
| 02 | [S3 Object Storage](modules/02-s3) | S3 | File Vault service (upload/download/presign/versioning) |
| 03 | [DynamoDB](modules/03-dynamodb) | DynamoDB, Streams | Task Tracker data layer |
| 04 | [SQS & SNS](modules/04-sqs-sns) | SQS, SNS | Order pipeline with fan-out + DLQ |
| 05 | [Lambda](modules/05-lambda) | Lambda, S3 events | S3-triggered metadata processor |
| 06 | [API Gateway + Lambda](modules/06-api-gateway) | API Gateway, Lambda, DynamoDB | Task Tracker REST API |
| 07 | [RDS](modules/07-rds) | RDS (Postgres) | Relational Users table + polyglot join |
| 08 | [ElastiCache](modules/08-elasticache) | ElastiCache (Redis) | Cache-aside layer for the Task Tracker API |
| 09 | [ECS / EC2](modules/09-ecs-ec2) | ECS, EC2 | Containerized Task Tracker API as an ECS service |
| 10 | [Infrastructure as Code](modules/10-cdk-iac) | CDK, CloudFormation | Redefine the Module 06 stack as code |
| 11 | [Testing, CI & Observability](modules/11-testing-ci) | CloudWatch Logs | Testcontainers suite + GitHub Actions pipeline |
| — | [Capstone: CloudMart](capstone-cloudmart) | All of the above | End-to-end serverless mini e-commerce platform |

Work through them in order — later modules reuse services and code from earlier ones
(the Task Tracker built in Module 03 keeps growing all the way to the capstone).
