# Module 04 — SQS & SNS (Task event fan-out)

## Theory

**SQS (queue) vs. SNS (topic)** solve different shapes of the same problem:
a queue is point-to-point (one message, consumed by exactly one worker, then
gone); a topic is pub/sub (one message, delivered to *every* subscriber).
The classic **fan-out pattern** combines them: publish once to an SNS topic,
subscribe several SQS queues to it, and each queue gets its own independent
copy to consume at its own pace — which is exactly what this module builds.

**Standard vs. FIFO queues:** standard queues (used here) give at-least-once
delivery and best-effort ordering; FIFO queues add exactly-once processing
and strict ordering within a message group, at lower throughput. Use FIFO
only when duplicate or out-of-order processing would actually break
correctness.

**Visibility timeout** is what makes retries possible without extra
bookkeeping: when a consumer receives a message, it becomes invisible to
other receivers for the timeout duration rather than being deleted. If the
consumer never explicitly deletes it (because processing failed), it
reappears after the timeout — exactly what `QueueConsumer` in this module
relies on.

**Dead-letter queues (DLQ)** catch messages that fail processing too many
times: a queue's `RedrivePolicy` names a target DLQ and a `maxReceiveCount`;
once a message has been received (and not deleted) that many times, SQS
moves it to the DLQ automatically. No consumer-side DLQ logic is needed —
you just don't delete on failure, and the queue does the rest.

**SNS filter policies** let a subscriber receive only a subset of a topic's
messages, matched against **message attributes** (not the message body).
This module's `task-events-notification-queue` subscribes with
`{"status":["DONE"]}`, so it only receives events published with a `status`
message attribute of `DONE` — the audit queue subscribes with no filter and
gets everything.

## What you'll build

`MessagingAdmin.bootstrap()` provisions:

```
                          ┌─ task-events-audit-queue         (no filter: gets everything)
task-events (SNS topic) ──┤
                          └─ task-events-notification-queue  (filter: status=DONE only)
                                    │ (on repeated failure)
                                    ▼
                             task-events-notification-dlq
```

- `TaskEvent` / `TaskEventCodec` — the message payload and its JSON (de)serialization.
- `TaskEventPublisher` — publishes to the topic with a `status` message attribute.
- `QueueConsumer` — receives, hands off to your handler, deletes only on success.
- `MessagingDemo` — publishes a mix of events, shows the audit queue getting all of
  them and the notification queue getting only the `DONE` ones, then forces a
  message to fail three times and shows it land on the DLQ.

Run it:

```bash
docker compose up -d
mvn -pl modules/04-sqs-sns compile exec:java
```

## Tests

```bash
mvn -pl modules/04-sqs-sns test              # TaskEventCodecTest - pure JSON round-trip
mvn -pl modules/04-sqs-sns test -Pfloci      # + fan-out and DLQ integration tests
```

- `FanOutIntegrationTest` — publishes one `IN_PROGRESS` and one `DONE` event,
  asserts the audit queue gets both and the notification queue (filtered)
  gets only the `DONE` one.
- `DeadLetterQueueIntegrationTest` — publishes one event, fails it on purpose
  repeatedly, and waits (via Awaitility, since the redrive isn't instantaneous)
  for it to show up on the DLQ.

## Checkpoint

- Why does the notification queue's subscription filter on a **message
  attribute** instead of parsing the JSON body to decide relevance?
- If `maxReceiveCount` were 1 instead of 3, what would change about how
  tolerant the pipeline is to a transient (not permanent) failure?
- What's the actual failure mode this module's DLQ protects against — what
  would happen to a permanently-broken message if there were no DLQ at all?

## Next

[Module 05 — Lambda](../05-lambda) — replace one of these polling consumers
with a function AWS invokes for you automatically.
