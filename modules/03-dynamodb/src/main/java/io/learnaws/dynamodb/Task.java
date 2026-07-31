package io.learnaws.dynamodb;

import java.time.Instant;

/**
 * The domain model the rest of the app works with. Deliberately separate from
 * {@link TaskItem} (the DynamoDB persistence bean) - the table's shape shouldn't
 * leak into application code, and vice versa.
 */
public record Task(
        String id,
        String title,
        String description,
        TaskStatus status,
        String owner,
        Instant createdAt,
        Instant updatedAt) {

    public static Task newTask(String id, String title, String description, String owner) {
        Instant now = Instant.now();
        return new Task(id, title, description, TaskStatus.TODO, owner, now, now);
    }

    public Task withStatus(TaskStatus newStatus) {
        return new Task(id, title, description, newStatus, owner, createdAt, Instant.now());
    }
}
