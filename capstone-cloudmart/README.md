# Capstone — CloudMart

A small serverless e-commerce platform that applies every module's pattern
to a second, independent domain: a product catalog and an order pipeline,
instead of the Task Tracker this curriculum built module by module.

## Architecture

```
                                POST /products, GET /products, GET /products/{id}
                                POST /orders,   GET /orders,   GET /orders/{id}
                                              │
                                    API Gateway (proxy)
                                              │
                                     CloudMartApiHandler ──────► cloudmart-products (DynamoDB)
                                              │                  cloudmart-orders   (DynamoDB)
                                              │                  cloudmart-product-images (S3)
                                              │
                                    publishes OrderEvent
                                              │
                                  cloudmart-order-events (SNS)
                                              │
                              cloudmart-order-processing-queue (SQS)
                                              │
                                  event source mapping (automatic)
                                              │
                                    OrderProcessorHandler ───────► cloudmart-orders (DynamoDB)
                                                                    PENDING -> CONFIRMED
```

Placing an order computes its total from the catalog price, saves it as
`PENDING`, and publishes an `OrderEvent`. That event lands on the processing
queue, which triggers `OrderProcessorHandler` automatically (an SQS event
source mapping - the same automation Modules 03/04 built by hand and Module
05 first showed AWS doing for you) - which confirms the order.

## Two ways to deploy the same infrastructure

- **`CloudMartDemo`** (imperative) — the same SDK-call-by-call bootstrap
  Modules 01-06 taught: `CloudMartIamBootstrap` (two least-privilege roles),
  table/bucket/topic/queue admin classes, `CloudMartLambdaDeployer`,
  `CloudMartEventSourceWiring`, `CloudMartApiGatewayAdmin`.
- **`cdk/CloudMartStack`** (declarative) — the same infrastructure as one
  CDK stack, Module 10's approach applied here. Compare its ~100 lines to
  every imperative admin class combined.

Both deploy the *same* Lambda handler classes
(`CloudMartApiHandler`, `OrderProcessorHandler`) - which infrastructure
approach provisions them is the only difference.

## Running it

Imperatively, against Floci directly:

```bash
docker compose up -d
mvn -pl capstone-cloudmart -am package    # builds target/cloudmart-lambda.jar
mvn -pl capstone-cloudmart compile exec:java
```

Or with the CDK (needs the `cdk` CLI - see Module 10's README for the full
`cdk bootstrap`/`cdk deploy` workflow against Floci):

```bash
cd capstone-cloudmart
cdk deploy
```

## Tests

```bash
mvn -pl capstone-cloudmart test              # pure unit tests + CloudMartApiRouterTest (mocked)
mvn -pl capstone-cloudmart verify            # + CloudMartStackIT (CDK synthesis, no Docker)
mvn -pl capstone-cloudmart test -Pfloci      # + CloudMartIntegrationTest (real DynamoDB/SNS/SQS)
```

- **`ProductMapperTest` / `OrderMapperTest` / `OrderEventCodecTest`** — pure
  mapping/serialization logic.
- **`CloudMartApiRouterTest`** — every route, with `ProductRepository`,
  `OrderRepository`, and `OrderEventPublisher` all mocked. Specifically
  verifies order totals are computed from the catalog price
  (`priceCents * quantity`), and that placing an order for a nonexistent
  product returns 400 without publishing anything.
- **`CloudMartStackIT`** — synthesizes the CDK stack and asserts on the
  resulting template: 2 tables (with the GSI), the bucket, topic, queue +
  subscription, both functions, the event source mapping, and the API's
  proxy resource. No Docker needed, same reasoning as Module 10.
- **`CloudMartIntegrationTest`** — real Floci-backed DynamoDB/SNS/SQS:
  creates a product and an order, and proves the `OrderEvent` actually
  lands on the processing queue with the correct computed total. Deploying
  both Lambda functions and driving the flow through the real API end to end
  follows exactly Module 06's `ApiGatewayDeploymentIT` pattern - left as an
  exercise rather than duplicated here.

## Where this capstone stops on purpose

It doesn't rebuild Module 07's RDS-backed accounts or Module 08's
ElastiCache session cache for this domain - those patterns are already
fully demonstrated there, and re-deriving them here would repeat code, not
teach anything new. Extending CloudMart with an RDS `customers` table (join
it with `cloudmart-orders` the way Module 07 joined Postgres users with
DynamoDB tasks) and an ElastiCache-backed cache for `GET /products/{id}`
(following Module 08's cache-aside pattern exactly) are natural next
exercises if you want to keep going.

## Checkpoint

- Trace one `POST /orders` request all the way to the order becoming
  `CONFIRMED` - which parts happen synchronously in the request, and which
  happen asynchronously afterward?
- `CloudMartStack` and `CloudMartDemo` provision identical infrastructure.
  If you needed to add a third resource (say, a DLQ for the processing
  queue), what would change in each, and which would you rather maintain
  going forward?
- What's actually different about `CloudMartApiRouterTest` versus
  `CloudMartIntegrationTest` in terms of what each one can catch?
