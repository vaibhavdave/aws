package io.learnaws.lambda;

import java.time.Instant;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Kept free of any Lambda-specific types (no {@code S3Event} here) so it's trivially unit
 * testable - the handler is a thin adapter that extracts primitives from the event and
 * hands them to this class.
 */
public final class FileMetadataService {

    private FileMetadataService() {
    }

    public static FileMetadata fromS3Record(String bucket, String key, long sizeBytes) {
        return new FileMetadata(bucket, key, sizeBytes, Instant.now());
    }

    public static void save(DynamoDbClient dynamoDb, FileMetadata metadata) {
        dynamoDb.putItem(b -> b
                .tableName(FileMetadataTableAdmin.TABLE_NAME)
                .item(Map.of(
                        "bucket", AttributeValue.fromS(metadata.bucket()),
                        "key", AttributeValue.fromS(metadata.key()),
                        "sizeBytes", AttributeValue.fromN(Long.toString(metadata.sizeBytes())),
                        "processedAt", AttributeValue.fromS(metadata.processedAt().toString()))));
    }
}
