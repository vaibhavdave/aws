package io.learnaws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Requires Docker. Run with: mvn test -Pfloci -pl modules/03-dynamodb
 */
@Tag("floci")
@Testcontainers
class TaskRepositoryIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    static TaskRepository repository;

    @BeforeAll
    static void setUpTable() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        DynamoDbClient dynamoDb = endpoint.configure(DynamoDbClient.builder());
        TaskTableAdmin.createTableIfNotExists(dynamoDb);

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        repository = new TaskRepository(enhancedClient);
    }

    @Test
    void saveThenFindByIdReturnsTheSameTask() {
        Task task = Task.newTask(UUID.randomUUID().toString(), "Write module 03", "DynamoDB", "alice");

        repository.save(task);

        assertThat(repository.findById(task.id())).contains(task);
    }

    @Test
    void findByOwnerUsesTheGsiToReturnOnlyThatOwnersTasks() {
        String owner = "owner-" + UUID.randomUUID();
        Task ownerTask1 = Task.newTask(UUID.randomUUID().toString(), "Task A", "d", owner);
        Task ownerTask2 = Task.newTask(UUID.randomUUID().toString(), "Task B", "d", owner);
        Task otherOwnerTask = Task.newTask(UUID.randomUUID().toString(), "Task C", "d", "someone-else");

        repository.save(ownerTask1);
        repository.save(ownerTask2);
        repository.save(otherOwnerTask);

        List<Task> tasks = repository.findByOwner(owner);

        assertThat(tasks).extracting(Task::id).containsExactlyInAnyOrder(ownerTask1.id(), ownerTask2.id());
    }

    @Test
    void updateStatusChangesStatusAndUpdatedAt() {
        Task task = Task.newTask(UUID.randomUUID().toString(), "Task", "d", "alice");
        repository.save(task);

        Task updated = repository.updateStatus(task.id(), TaskStatus.DONE);

        assertThat(updated.status()).isEqualTo(TaskStatus.DONE);
        assertThat(repository.findById(task.id())).get().extracting(Task::status).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void deleteRemovesTheItem() {
        Task task = Task.newTask(UUID.randomUUID().toString(), "Task", "d", "alice");
        repository.save(task);

        repository.delete(task.id());

        assertThat(repository.findById(task.id())).isEmpty();
    }
}
