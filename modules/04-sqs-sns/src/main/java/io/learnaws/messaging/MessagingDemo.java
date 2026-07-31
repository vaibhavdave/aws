package io.learnaws.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.learnaws.dynamodb.TaskStatus;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Run with: mvn -pl modules/04-sqs-sns compile exec:java
 */
public final class MessagingDemo {

    public static void main(String[] args) throws InterruptedException {
        FlociEndpoint floci = FlociEndpoint.local();

        try (SnsClient sns = floci.configure(SnsClient.builder());
                SqsClient sqs = floci.configure(SqsClient.builder())) {

            MessagingAdmin.Resources resources = MessagingAdmin.bootstrap(sns, sqs);
            TaskEventPublisher publisher = new TaskEventPublisher(sns, resources.topicArn());

            publisher.publish(new TaskEvent(UUID.randomUUID().toString(), "alice", TaskStatus.IN_PROGRESS, Instant.now()));
            publisher.publish(new TaskEvent(UUID.randomUUID().toString(), "alice", TaskStatus.DONE, Instant.now()));
            publisher.publish(new TaskEvent(UUID.randomUUID().toString(), "bob", TaskStatus.DONE, Instant.now()));
            System.out.println("Published 3 events (1 IN_PROGRESS, 2 DONE).");

            Thread.sleep(500); // let Floci's in-process fan-out settle

            QueueConsumer auditConsumer = new QueueConsumer(sqs, resources.auditQueueUrl());
            List<TaskEvent> audited = auditConsumer.poll(10, Duration.ofSeconds(2), e -> System.out.println("  [audit] " + e));
            System.out.println("Audit queue received " + audited.size() + " events (no filter - gets everything).");

            QueueConsumer notificationConsumer = new QueueConsumer(sqs, resources.notificationQueueUrl());
            List<TaskEvent> notified = notificationConsumer.poll(10, Duration.ofSeconds(2), e -> System.out.println("  [notify] " + e));
            System.out.println("Notification queue received " + notified.size()
                    + " events (FilterPolicy status=DONE - only the completions).");

            // Now demonstrate the DLQ: publish one more DONE event, but make the notification
            // handler always throw, forcing it through the redrive policy.
            TaskEvent poison = new TaskEvent(UUID.randomUUID().toString(), "carol", TaskStatus.DONE, Instant.now());
            publisher.publish(poison);
            Thread.sleep(500);

            for (int attempt = 1; attempt <= 3; attempt++) {
                notificationConsumer.poll(10, Duration.ofSeconds(1), e -> {
                    throw new RuntimeException("simulated processing failure");
                });
                System.out.println("Attempt " + attempt + " to process the poison message failed on purpose.");
                Thread.sleep(1500); // exceed the queue's 1s visibility timeout before retrying
            }

            QueueConsumer dlqConsumer = new QueueConsumer(sqs, resources.notificationDlqUrl());
            List<TaskEvent> deadLettered = dlqConsumer.poll(10, Duration.ofSeconds(2), e -> System.out.println("  [dlq] " + e));
            System.out.println("Dead-letter queue received " + deadLettered.size()
                    + " event(s) after exceeding maxReceiveCount=3.");
        }
    }
}
