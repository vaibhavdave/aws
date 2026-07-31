package io.learnaws.cloudmart.catalog;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public final class ProductTableAdmin {

    public static final String TABLE_NAME = "cloudmart-products";

    private ProductTableAdmin() {
    }

    public static void createTableIfNotExists(DynamoDbClient dynamoDb) {
        try {
            dynamoDb.createTable(b -> b
                    .tableName(TABLE_NAME)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build()));
        } catch (ResourceInUseException alreadyExists) {
            // already provisioned
        }
    }
}
