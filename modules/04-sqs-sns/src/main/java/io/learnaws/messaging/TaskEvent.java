package io.learnaws.messaging;

import java.time.Instant;

import io.learnaws.dynamodb.TaskStatus;

/** Published to the "task-events" SNS topic whenever a Task Tracker task changes status. */
public record TaskEvent(String taskId, String owner, TaskStatus status, Instant occurredAt) {
}
