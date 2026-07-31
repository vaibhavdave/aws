package io.learnaws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/**
 * Requires Docker. Run with: mvn test -Pfloci -pl modules/03-dynamodb
 */
@Tag("floci")
@Testcontainers
class TaskStreamIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void insertingATaskShowsUpOnTheDynamoDbStream() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        DynamoDbClient dynamoDb = endpoint.configure(DynamoDbClient.builder());
        DynamoDbStreamsClient streams = endpoint.configure(DynamoDbStreamsClient.builder());
        TaskTableAdmin.createTableIfNotExists(dynamoDb);

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        TaskRepository repository = new TaskRepository(enhancedClient);

        Task inserted = Task.newTask(UUID.randomUUID().toString(), "Streamed task", "d", "alice");
        repository.save(inserted);

        String streamArn = TaskTableAdmin.latestStreamArn(dynamoDb);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Task> streamedInserts = TaskStreamPoller.pollNewTasks(streams, streamArn);
            assertThat(streamedInserts).extracting(Task::id).contains(inserted.id());
        });
    }
}
