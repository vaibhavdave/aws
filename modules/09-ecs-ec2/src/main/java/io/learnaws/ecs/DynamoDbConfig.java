package io.learnaws.ecs;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskTableAdmin;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Unlike Modules 07/08, this builds the DynamoDB client with no explicit endpoint override:
 * this app runs *inside* the ECS-managed container, so (like Module 05's Lambda) it relies
 * on AWS_ENDPOINT_URL being set in the container's environment (see EcsDeployer) rather than
 * FlociEndpoint.local(), which assumes it's running on the same host as Floci.
 */
@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder().build();
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
