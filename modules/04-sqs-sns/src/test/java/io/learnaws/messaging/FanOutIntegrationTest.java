package io.learnaws.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.dynamodb.TaskStatus;
import io.learnaws.foundations.FlociEndpoint;
import org.junit.jupiter.api.Tag;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Requires Docker. Run with: mvn test -Pfloci -pl modules/04-sqs-sns
 */
@Tag("floci")
@Testcontainers
class FanOutIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void auditQueueGetsEverythingNotificationQueueGetsOnlyDoneEvents() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        SnsClient sns = endpoint.configure(SnsClient.builder());
        SqsClient sqs = endpoint.configure(SqsClient.builder());

        MessagingAdmin.Resources resources = MessagingAdmin.bootstrap(sns, sqs);
        TaskEventPublisher publisher = new TaskEventPublisher(sns, resources.topicArn());

        String taskId1 = UUID.randomUUID().toString();
        String taskId2 = UUID.randomUUID().toString();
        publisher.publish(new TaskEvent(taskId1, "alice", TaskStatus.IN_PROGRESS, Instant.now()));
        publisher.publish(new TaskEvent(taskId2, "alice", TaskStatus.DONE, Instant.now()));

        QueueConsumer auditConsumer = new QueueConsumer(sqs, resources.auditQueueUrl());
        QueueConsumer notificationConsumer = new QueueConsumer(sqs, resources.notificationQueueUrl());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<TaskEvent> audited = auditConsumer.poll(10, Duration.ofSeconds(1), e -> { });
            assertThat(audited).extracting(TaskEvent::taskId).contains(taskId1, taskId2);
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<TaskEvent> notified = notificationConsumer.poll(10, Duration.ofSeconds(1), e -> { });
            assertThat(notified).extracting(TaskEvent::taskId).contains(taskId2).doesNotContain(taskId1);
        });
    }
}
