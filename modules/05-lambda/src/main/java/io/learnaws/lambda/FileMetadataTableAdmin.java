package io.learnaws.lambda;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public final class FileMetadataTableAdmin {

    public static final String TABLE_NAME = "file-metadata";

    private FileMetadataTableAdmin() {
    }

    public static void createTableIfNotExists(DynamoDbClient dynamoDb) {
        try {
            dynamoDb.createTable(b -> b
                    .tableName(TABLE_NAME)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("bucket").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("key").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("bucket").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("key").keyType(KeyType.RANGE).build()));
        } catch (ResourceInUseException alreadyExists) {
            // already provisioned
        }
    }
}
