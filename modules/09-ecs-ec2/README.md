# Module 09 — ECS / EC2 (containerized Task Tracker service)

## Theory

**EC2** is raw virtual machines: you pick an instance type (a CPU/memory/network
bundle, e.g. `t3.micro`) and an AMI (the disk image it boots from), and you
manage everything above the hypervisor — OS patches, runtime, your app.

**ECS runs containers, not VMs directly**, via two launch types:

- **EC2 launch type** — containers run on EC2 instances *you* register into
  the cluster (you manage the underlying fleet, ECS just schedules containers
  onto it).
- **Fargate launch type** — no EC2 instances to manage at all; you describe
  the task's CPU/memory and AWS runs it. This module uses Fargate, since it
  avoids also having to provision and register EC2 container instances just
  to run one small service.

**Task definitions vs. services vs. tasks:** a **task definition** is a
blueprint (image, CPU/memory, port mappings, environment) — like a class. A
**task** is one running instance of it — like an object. A **service** keeps
a desired number of tasks running continuously, replacing any that die; a
one-off task run without a service just runs once and stops. This module
creates a service with `desiredCount: 1`.

**Why Fargate needs a VPC even for one container:** Fargate tasks use
`awsvpc` network mode — each task gets its own elastic network interface,
which has to attach to *something*, hence a real subnet and security group
are required even for a single-container demo. That's genuinely EC2 API
surface (VPCs/subnets/security groups all live in the EC2 service namespace),
which is why `NetworkProvisioner` in this "ECS module" is calling `Ec2Client`.

## What you'll build

The same Task Tracker CRUD API as Module 06, but as a **long-running
container** instead of a Lambda function (`TaskTrackerContainerApplication`,
a plain Spring Boot app — no proxy-integration event handling needed, since
nothing is invoking it per-request the way Lambda is).

- **`Dockerfile`** — packages the Spring Boot fat jar into a small JRE image,
  with a `HEALTHCHECK` hitting `/healthz` (which ECS also uses for its own
  container health checks).
- **`NetworkProvisioner`** — finds-or-creates the VPC/subnet/security group
  Fargate needs.
- **`EcsDeployer`** — creates the cluster, registers the task definition
  (image, port mapping, `AWS_ENDPOINT_URL` so the container's own DynamoDB
  calls reach Floci), and creates (or updates) the service, then waits for it
  to reach steady state.

## Running it

```bash
mvn -pl modules/09-ecs-ec2 -am package
docker build -t task-tracker-container:latest modules/09-ecs-ec2
docker compose up -d
mvn -pl modules/09-ecs-ec2 compile exec:java
```

Unlike every prior module, this one has a manual `docker build` step before
the demo: ECS needs an actual image to schedule, and this repo doesn't push
one to a registry — the task definition here points at a plain local image
tag, standing in for what would be an ECR image URI on real AWS.

## Tests

```bash
mvn -pl modules/09-ecs-ec2 test              # TaskControllerTest - repository mocked, no Floci
mvn -pl modules/09-ecs-ec2 test -Pfloci      # + the real ECS/Fargate deployment (needs the image built first)
```

`TaskControllerTest` is a `@WebMvcTest` over the container's own REST
controller with `TaskRepository` mocked - verifies the app's HTTP behavior
with no infrastructure at all. `EcsIntegrationTest` provisions the real
network, deploys the real service, and asserts it reaches steady state with
`runningCount == desiredCount` - deliberately not chasing down the running
task's actual IP to call it over HTTP, since that requires solving
container-network reachability this curriculum hasn't introduced a load
balancer for yet (a natural next step, not attempted here).

## Checkpoint

- Why does a Fargate task need a security group and subnet, when "there's no
  EC2 instance" involved?
- What's the practical difference between running a task definition once
  directly, versus wrapping it in a service with `desiredCount: 1`?
- If you wanted callers to reach this service without knowing its task's
  private IP, what AWS resource would you put in front of it?

## Next

[Module 10 — Infrastructure as Code](../10-cdk-iac) — stop clicking together
resources by hand and define the Task Tracker API's stack as code with the
AWS CDK.
