package io.learnaws.rds;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskTableAdmin;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Wires up Module 03's DynamoDB Task Tracker table as a bean this module's controller can query. */
@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return FlociEndpoint.local().configure(DynamoDbClient.builder());
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    public TaskRepository taskRepository(DynamoDbEnhancedClient enhancedClient) {
        return new TaskRepository(enhancedClient);
    }

    @Bean
    public ApplicationRunner taskTableInitializer(DynamoDbClient dynamoDbClient) {
        return args -> TaskTableAdmin.createTableIfNotExists(dynamoDbClient);
    }
}
