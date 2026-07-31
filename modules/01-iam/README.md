# Module 01 — IAM & Security

## Theory

**IAM's building blocks:**

- **User** — a long-lived identity, usually a human or a legacy static-credential
  integration. Avoid for workloads where you can help it.
- **Role** — an identity with no long-term credentials at all; something else
  (a person, an EC2 instance, a Lambda function, another AWS account) *assumes*
  it to get short-lived credentials. This is how virtually all AWS-to-AWS access
  should work.
- **Group** — a bundle of users sharing the same permissions. Not used in this
  module, but worth knowing it exists for user-management scenarios.
- **Policy** — a JSON document listing `Effect` / `Action` / `Resource` statements.
  Two flavors matter here:
  - **Identity-based policy** — attached to a user/role/group, says what *it*
    can do.
  - **Trust policy** (a role's `AssumeRolePolicyDocument`) — attached to a role,
    says *who is allowed to assume it*. Every role has exactly one.

**Policy evaluation, in one sentence:** everything is implicitly denied unless an
`Allow` statement matches; an explicit `Deny` anywhere always wins over any
`Allow`. Least privilege means writing policies that only allow exactly the
actions and resources a workload needs — not `"Action": "*"` on
`"Resource": "*"`.

**STS AssumeRole** is the mechanic that turns "I have permission to assume role
X" into actual usable credentials: it returns a temporary access key, secret
key, and session token, all with an expiry. Code that holds these looks
identical to code holding a long-lived access key — you just wrap them in
`AwsSessionCredentials` instead of `AwsBasicCredentials`.

## What you'll build

`IamBootstrap` provisions exactly the identity this whole curriculum's demo
app ("Task Tracker") runs under:

- **`task-tracker-app-policy`** (`src/main/resources/policies/task-tracker-app-policy.json`)
  — grants only `dynamodb:GetItem/PutItem/UpdateItem/DeleteItem/Query` on the
  `task-tracker` table (Module 03) and only `s3:GetObject/PutObject` on the
  `task-tracker-files` bucket (Module 02). Nothing else.
- **`task-tracker-app-role`** (`src/main/resources/policies/task-tracker-trust-policy.json`)
  — trusts the account root to assume it, the simplest trust relationship there
  is. (Module 05's Lambda role will instead trust the `lambda.amazonaws.com`
  service principal — a variant of the same mechanic.)

`IamBootstrapDemo` then:

1. Creates the policy and role (idempotently — safe to re-run).
2. Attaches the policy to the role.
3. Calls `sts:AssumeRole` to exchange the role ARN for temporary session credentials.
4. Builds a *second* STS client using only those temporary credentials and calls
   `GetCallerIdentity` again — proving the ARN it reports has changed from your
   original identity to `assumed-role/task-tracker-app-role/...`.

Run it:

```bash
docker compose up -d      # from repo root, if not already running
mvn -pl modules/01-iam compile exec:java
```

## A note on enforcement

Floci models the IAM *control plane* faithfully — creating roles/policies,
attaching them, and the AssumeRole exchange all behave exactly like real AWS,
which is what this module (and the code you're writing) is actually about.
Whether every downstream service enforces those policy documents byte-for-byte
against every possible action is emulator-specific and evolves over time —
don't take a lack of a `AccessDenied` locally as proof a policy is correct.
The JSON you're authoring here is real IAM policy syntax; treat "does this
policy actually restrict what it should" as a question to verify against a
real AWS account (or `iam:SimulatePrincipalPolicy` there) before you trust it
in production. We'll revisit this policy again once Modules 02 and 03 build
the S3 bucket and DynamoDB table it references.

## Tests

```bash
mvn -pl modules/01-iam test              # PolicyDocumentsTest - just validates the JSON shape
mvn -pl modules/01-iam test -Pfloci       # + IamBootstrapIntegrationTest against a real Floci instance
```

## Checkpoint

- Why does a role have no long-term credentials, while a user can?
- In the trust policy, what would you change to let a Lambda function (rather
  than the account root) assume this role?
- If a policy has one `Allow *` statement and one narrow `Deny` statement on the
  same action, which wins?

## Next

[Module 02 — S3 Object Storage](../02-s3) — build the File Vault service that
this module's role is scoped to access.
