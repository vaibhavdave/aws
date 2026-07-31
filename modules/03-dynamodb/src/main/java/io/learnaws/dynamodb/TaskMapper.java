package io.learnaws.dynamodb;

import java.time.Instant;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Pure conversions between the domain model, the persistence bean, and raw Streams records. */
public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskItem toItem(Task task) {
        TaskItem item = new TaskItem();
        item.setId(task.id());
        item.setTitle(task.title());
        item.setDescription(task.description());
        item.setStatus(task.status().name());
        item.setOwner(task.owner());
        item.setCreatedAt(task.createdAt().toString());
        item.setUpdatedAt(task.updatedAt().toString());
        return item;
    }

    public static Task toDomain(TaskItem item) {
        return new Task(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                TaskStatus.valueOf(item.getStatus()),
                item.getOwner(),
                Instant.parse(item.getCreatedAt()),
                Instant.parse(item.getUpdatedAt()));
    }

    /** Rebuilds a Task from a DynamoDB Streams NewImage/OldImage - the same field names as TaskItem. */
    public static Task fromStreamImage(Map<String, AttributeValue> image) {
        return new Task(
                image.get("id").s(),
                image.get("title").s(),
                image.get("description").s(),
                TaskStatus.valueOf(image.get("status").s()),
                image.get("owner").s(),
                Instant.parse(image.get("createdAt").s()),
                Instant.parse(image.get("updatedAt").s()));
    }
}
