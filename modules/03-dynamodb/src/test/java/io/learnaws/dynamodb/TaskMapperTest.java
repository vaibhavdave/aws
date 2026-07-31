package io.learnaws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Pure conversions, no Floci or network required. */
class TaskMapperTest {

    @Test
    void toItemAndBackToDomainRoundTrips() {
        Task task = Task.newTask("task-1", "Write tests", "Cover the mapper", "alice");

        TaskItem item = TaskMapper.toItem(task);
        assertThat(item.getId()).isEqualTo("task-1");
        assertThat(item.getStatus()).isEqualTo("TODO");

        Task roundTripped = TaskMapper.toDomain(item);
        assertThat(roundTripped).isEqualTo(task);
    }

    @Test
    void fromStreamImageRebuildsATaskFromRawAttributeValues() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, AttributeValue> image = Map.of(
                "id", AttributeValue.fromS("task-2"),
                "title", AttributeValue.fromS("Ship it"),
                "description", AttributeValue.fromS("Deploy to prod"),
                "status", AttributeValue.fromS("DONE"),
                "owner", AttributeValue.fromS("bob"),
                "createdAt", AttributeValue.fromS(now.toString()),
                "updatedAt", AttributeValue.fromS(now.toString()));

        Task task = TaskMapper.fromStreamImage(image);

        assertThat(task).isEqualTo(new Task("task-2", "Ship it", "Deploy to prod", TaskStatus.DONE, "bob", now, now));
    }

    @Test
    void withStatusBumpsUpdatedAtButKeepsEverythingElse() {
        Task task = Task.newTask("task-3", "Title", "Description", "alice");

        Task inProgress = task.withStatus(TaskStatus.IN_PROGRESS);

        assertThat(inProgress.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(inProgress.id()).isEqualTo(task.id());
        assertThat(inProgress.createdAt()).isEqualTo(task.createdAt());
    }
}
