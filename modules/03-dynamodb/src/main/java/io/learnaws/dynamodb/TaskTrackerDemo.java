package io.learnaws.dynamodb;

import java.util.List;
import java.util.UUID;

import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/**
 * Run with: mvn -pl modules/03-dynamodb compile exec:java
 */
public final class TaskTrackerDemo {

    public static void main(String[] args) throws InterruptedException {
        FlociEndpoint floci = FlociEndpoint.local();

        try (DynamoDbClient dynamoDb = floci.configure(DynamoDbClient.builder());
                DynamoDbStreamsClient streams = floci.configure(DynamoDbStreamsClient.builder())) {

            TaskTableAdmin.createTableIfNotExists(dynamoDb);

            DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
            TaskRepository repository = new TaskRepository(enhancedClient);

            Task first = Task.newTask(UUID.randomUUID().toString(), "Write module 03 README", "DynamoDB theory", "alice");
            Task second = Task.newTask(UUID.randomUUID().toString(), "Review PR", "Check the Task Tracker API", "alice");
            Task third = Task.newTask(UUID.randomUUID().toString(), "Deploy Floci", "docker compose up", "bob");

            repository.save(first);
            repository.save(second);
            repository.save(third);
            System.out.println("Saved 3 tasks.");

            System.out.println("findById(second): " + repository.findById(second.id()).orElseThrow());

            List<Task> aliceTasks = repository.findByOwner("alice");
            System.out.println("alice's tasks via owner-index GSI (" + aliceTasks.size() + "): " + aliceTasks);

            Task updated = repository.updateStatus(first.id(), TaskStatus.IN_PROGRESS);
            System.out.println("Updated status: " + updated);

            // Give hybrid-mode storage a moment, then read back what landed on the stream.
            Thread.sleep(500);
            String streamArn = TaskTableAdmin.latestStreamArn(dynamoDb);
            List<Task> streamedInserts = TaskStreamPoller.pollNewTasks(streams, streamArn);
            System.out.println("New INSERTs seen on the DynamoDB Stream (" + streamedInserts.size() + "): " + streamedInserts);

            repository.delete(third.id());
            System.out.println("Deleted task " + third.id());
        }
    }
}
