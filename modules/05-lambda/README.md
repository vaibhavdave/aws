# Module 05 — Lambda (S3-triggered file metadata processor)

## Theory

**FaaS in one sentence:** you deploy a function, not a server; the platform
runs it in response to events, scales it out per-concurrent-invocation, and
you pay per invocation + duration instead of per running instance. No
process is "always on" the way an EC2 instance or a Spring Boot app is.

**Execution model:** a request arrives → if no warm execution environment
exists, AWS provisions one (a **cold start**: download your code, start the
JVM, run static initializers) → your handler runs → the environment may be
kept warm to serve the next request without re-paying the cold-start cost.
This is why `FileMetadataHandler` builds its `DynamoDbClient` as an instance
field initialized once, not inside `handleRequest` — reuse across warm
invocations, exactly like you would with any long-lived connection pool.

**Packaging (Java specifically):** a Lambda deployment package is a zip/jar
containing your compiled classes plus every dependency *not already on the
runtime's classpath*. `RequestHandler`/`Context` (from
`aws-lambda-java-core`) are guaranteed present — that's `provided` scope in
this module's `pom.xml`. Everything else (the AWS SDK, `aws-lambda-java-events`
for typed event classes like `S3Event`) must be shaded into your jar, which
is what the `maven-shade-plugin` execution here does, producing
`target/lambda-file-processor.jar`.

**Event sources:** something outside Lambda decides to invoke your function.
This module wires an **S3 event notification** (`s3:ObjectCreated:*`) to
invoke it directly; other common sources are SQS/DynamoDB Streams (pull-based
event source mappings — the same DynamoDB Streams protocol Module 03's
`TaskStreamPoller` reads by hand, but with AWS polling and invoking for you)
and API Gateway (Module 06).

## What you'll build

- **`FileMetadataHandler`** — `RequestHandler<S3Event, String>`. Deliberately
  thin: for each S3 record, it pulls out `bucket`/`key`/`size` and hands them
  to `FileMetadataService` (plain, Lambda-agnostic logic + a DynamoDB write) —
  keeping the actual business logic unit-testable without needing a Lambda
  runtime at all.
- **`LambdaIamBootstrap`** — the execution role variant Module 01 flagged as
  coming later: trust policy for the `lambda.amazonaws.com` service principal
  (not the account root), permissions policy scoped to `dynamodb:PutItem` on
  the new `file-metadata` table.
- **`LambdaDeployer`** — packages/deploys: `CreateFunction` (or
  `UpdateFunctionCode` + `UpdateFunctionConfiguration` if it already exists),
  then waits for the function to reach `Active` before returning its ARN.
- **`S3NotificationWiring`** — grants S3 permission to invoke the function
  (`lambda:AddPermission`) and configures the bucket's notification config
  (`s3:PutBucketNotificationConfiguration`) — the two steps real S3-to-Lambda
  wiring always needs.

### A networking detail worth understanding, not memorizing

Floci runs Lambda as a **real Docker container**. Code running inside that
container can't reach your host's `localhost:4566` the way your own JVM can —
it needs a route back to Floci over the Docker network. This module's
`LambdaDeployer` call sets the function's `AWS_ENDPOINT_URL` environment
variable to `http://floci:4566` (`floci` being this repo's
`docker-compose.yml` service/container name), and the AWS SDK v2 honors that
variable automatically wherever `DynamoDbClient.builder().build()` is called
with no explicit endpoint override — which is exactly what `FileMetadataHandler`
does, so the same handler code is what you'd deploy to real AWS unchanged
(where that variable simply wouldn't be set). If your own setup names or
networks Floci differently, that's the one line to adjust.

## Running it

```bash
docker compose up -d
mvn -pl modules/05-lambda package          # builds target/lambda-file-processor.jar
mvn -pl modules/05-lambda compile exec:java
```

The first deploy pulls the `public.ecr.aws/lambda/java21` base image, which
can take a minute; after that it's fast.

## Tests

```bash
mvn -pl modules/05-lambda test               # FileMetadataServiceTest - pure, instant
mvn -pl modules/05-lambda verify -Pfloci     # + the real end-to-end deployment
```

`LambdaDeploymentIT` is a **failsafe** integration test, not a surefire one:
it needs `target/lambda-file-processor.jar` to already exist, which only
happens after the `package` phase — surefire (`mvn test`) runs *before*
`package` in the Maven lifecycle, so this is the first module where that
ordering actually matters. It also intentionally doesn't spin up an ephemeral
Testcontainers Floci like this curriculum's other integration tests: since
the deployed function needs to reach back into Floci over a predictable
Docker network, it targets the `docker compose up` instance directly instead.

## Checkpoint

- Why is `aws-lambda-java-core` `provided` scope but `aws-lambda-java-events`
  isn't?
- What would happen to `FileMetadataHandler` if you deployed it to real AWS
  Lambda without setting `AWS_ENDPOINT_URL`?
- Why does `FileMetadataService` take no Lambda-specific types as parameters?

## Next

[Module 06 — API Gateway + Lambda](../06-api-gateway) — trigger a Lambda from
an HTTP request instead of an S3 event, building a real REST API over the
Task Tracker table.
