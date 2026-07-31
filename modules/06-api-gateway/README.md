# Module 06 — API Gateway + Lambda (Task Tracker REST API)

## Theory

**API Gateway REST API structure:** a **resource tree** (`/tasks`, `/tasks/{id}`
— `{id}` is a path parameter placeholder), each resource has one or more
**methods** (`GET`, `POST`, ...), each method has an **integration** telling
API Gateway what to actually call. A **deployment** snapshots that
configuration; a **stage** (e.g. `prod`) is a named, invokable instance of a
deployment. Nothing is publicly reachable until you deploy to a stage.

**Lambda proxy integration (`AWS_PROXY`)** is the integration type this
module uses everywhere: API Gateway hands your Lambda the *entire* request
(method, path, headers, query string, path parameters, body) as one JSON
event (`APIGatewayProxyRequestEvent`), and your function is fully responsible
for the response shape (`APIGatewayProxyResponseEvent`, including the status
code and headers). The alternative — non-proxy integration with request/response
*mapping templates* (VTL) on the API Gateway side — pushes that translation
into API Gateway configuration instead of your code; proxy integration is
simpler and is what this module (and most modern Lambda-backed APIs) uses.

**Why one Lambda function for every route**, instead of one function per
endpoint? Both are valid designs. This module's single-function approach
means one deployable unit and one warm-container pool to reason about;
Lambda handles routing/method dispatch it- self (`TaskApiRouter`). A
one-function-per-route design isolates blast radius and cold starts per
endpoint at the cost of more moving pieces — a real tradeoff worth knowing
you're making, not a default to follow blindly.

## What you'll build

Reuses two earlier modules directly: `Task`/`TaskStatus`/`TaskRepository`/
`TaskTableAdmin` from **Module 03**, and `LambdaDeployer` from **Module 05**
(now generalized to take a `handler` string, since it's no longer specific to
one function).

```
POST   /tasks              create a task            (body: title, description, owner)
GET    /tasks?owner=alice  list one owner's tasks    (uses the owner-index GSI)
GET    /tasks/{id}         get one task
PATCH  /tasks/{id}         update status             (body: {"status": "DONE"})
DELETE /tasks/{id}         delete a task
```

- **`TaskApiRouter`** — all the actual logic: parses the proxy event, calls
  `TaskRepository`, builds the JSON response. No Lambda-runtime dependency in
  its reasoning beyond the event/response types themselves, and it takes a
  `TaskRepository` by constructor injection — so it's testable with a mocked
  repository (see `TaskApiRouterTest`).
- **`TaskApiHandler`** — the thin `RequestHandler` adapter: builds the real
  `TaskRepository` once (warm-container reuse, same reasoning as Module 05),
  delegates every invocation to the router.
- **`ApiIamBootstrap`** — a Lambda-trusted role scoped to CRUD on the
  `task-tracker` table (the same permissions Module 01's original
  `task-tracker-app-policy` granted, reissued for a role Lambda can assume).
- **`ApiGatewayAdmin`** — builds the resource tree, wires `AWS_PROXY`
  integrations, grants API Gateway permission to invoke the function, and
  deploys a `prod` stage.

## Running it

```bash
docker compose up -d
mvn -pl modules/06-api-gateway package
mvn -pl modules/06-api-gateway compile exec:java
```

The demo deploys everything and drives the full CRUD lifecycle with plain
`java.net.http.HttpClient` calls, printing each response.

## Tests

```bash
mvn -pl modules/06-api-gateway test              # TaskApiRouterTest - mocked repository, no Floci
mvn -pl modules/06-api-gateway verify -Pfloci    # + the real deployed API, end-to-end
```

`TaskApiRouterTest` covers all five routes plus error cases (missing
required field, invalid status value, unknown route) against a Mockito-mocked
`TaskRepository` — fast, deterministic, no infrastructure. `ApiGatewayDeploymentIT`
(a failsafe IT, same reasoning as Module 05's `LambdaDeploymentIT` — needs the
packaged jar, and targets the `docker compose` Floci directly for predictable
Lambda-to-Floci networking) drives the same lifecycle through the actual
deployed REST API.

## Checkpoint

- What part of an HTTP request does `AWS_PROXY` integration hand to your
  Lambda that a non-proxy integration would instead try to map for you?
- Why is `TaskApiRouter` a separate class from `TaskApiHandler`, when the
  handler could just inline the routing logic?
- If you added a `PUT /tasks/{id}` route for a full replace (not just status),
  what would you need to change in `ApiGatewayAdmin`, and separately in
  `TaskApiRouter`?

## Next

[Module 07 — RDS](../07-rds) — add a relational Users table alongside the
DynamoDB Task Tracker table, and see polyglot persistence in one application.
