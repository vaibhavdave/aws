package io.learnaws.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.dynamodb.TaskStatus;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Requires Docker. Run with: mvn test -Pfloci -pl modules/04-sqs-sns
 */
@Tag("floci")
@Testcontainers
class DeadLetterQueueIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void aMessageThatAlwaysFailsToProcessEndsUpOnTheDlq() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        SnsClient sns = endpoint.configure(SnsClient.builder());
        SqsClient sqs = endpoint.configure(SqsClient.builder());

        MessagingAdmin.Resources resources = MessagingAdmin.bootstrap(sns, sqs);
        TaskEventPublisher publisher = new TaskEventPublisher(sns, resources.topicArn());

        String poisonTaskId = UUID.randomUUID().toString();
        publisher.publish(new TaskEvent(poisonTaskId, "carol", TaskStatus.DONE, Instant.now()));

        QueueConsumer notificationConsumer = new QueueConsumer(sqs, resources.notificationQueueUrl());

        // The queue's RedrivePolicy has maxReceiveCount=3 and a 1s visibility timeout;
        // fail on purpose enough times, with enough delay in between, to exceed it.
        await().atMost(Duration.ofSeconds(20)).pollDelay(Duration.ofSeconds(2)).untilAsserted(() -> {
            notificationConsumer.poll(10, Duration.ofSeconds(1), e -> {
                throw new RuntimeException("simulated processing failure");
            });

            QueueConsumer dlqConsumer = new QueueConsumer(sqs, resources.notificationDlqUrl());
            List<TaskEvent> deadLettered = dlqConsumer.poll(10, Duration.ofSeconds(1), e -> { });

            assertThat(deadLettered).extracting(TaskEvent::taskId).contains(poisonTaskId);
        });
    }
}
