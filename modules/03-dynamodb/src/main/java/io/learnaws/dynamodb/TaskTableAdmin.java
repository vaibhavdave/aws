package io.learnaws.dynamodb;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamSpecification;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;

/**
 * Table DDL (create/describe) lives on the low-level {@link DynamoDbClient}; CRUD on the
 * enhanced client in {@link TaskRepository}. Real applications typically split these the
 * same way - schema management is an infra/deploy concern, not a runtime one.
 */
public final class TaskTableAdmin {

    private TaskTableAdmin() {
    }

    public static void createTableIfNotExists(DynamoDbClient dynamoDb) {
        try {
            dynamoDb.createTable(b -> b
                    .tableName(TaskRepository.TABLE_NAME)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("owner").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("createdAt").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName(TaskRepository.OWNER_INDEX)
                            .keySchema(
                                    KeySchemaElement.builder().attributeName("owner").keyType(KeyType.HASH).build(),
                                    KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build())
                    .streamSpecification(StreamSpecification.builder()
                            .streamEnabled(true)
                            .streamViewType(StreamViewType.NEW_AND_OLD_IMAGES)
                            .build()));
        } catch (ResourceInUseException alreadyExists) {
            // table (and its GSI/stream) already provisioned - nothing to do
        }
    }

    /** Null once the table exists but streams were somehow disabled; non-null in the normal case. */
    public static String latestStreamArn(DynamoDbClient dynamoDb) {
        try {
            return dynamoDb.describeTable(b -> b.tableName(TaskRepository.TABLE_NAME))
                    .table()
                    .latestStreamArn();
        } catch (ResourceNotFoundException notFound) {
            throw new IllegalStateException(
                    "Table " + TaskRepository.TABLE_NAME + " does not exist - call createTableIfNotExists first", notFound);
        }
    }
}
