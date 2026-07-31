package io.learnaws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Deployed to Floci as a real Lambda function, triggered by S3 ObjectCreated events on the
 * "task-tracker-files" bucket (see {@link S3NotificationWiring}). AWS Lambda instantiates
 * this class with its no-arg constructor once per (warm) execution environment.
 *
 * DynamoDbClient.builder().build() with no explicit endpoint override is deliberate: inside
 * the deployed function it honors the AWS_ENDPOINT_URL environment variable that
 * {@link LambdaDeployer} sets on the function, so this same code is what you'd ship to real
 * AWS unchanged (there, no such variable is set, and it talks to real DynamoDB).
 */
public class FileMetadataHandler implements RequestHandler<S3Event, String> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.builder().build();

    @Override
    public String handleRequest(S3Event event, Context context) {
        int processed = 0;
        for (S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getUrlDecodedKey();
            long sizeBytes = record.getS3().getObject().getSizeAsLong();

            FileMetadata metadata = FileMetadataService.fromS3Record(bucket, key, sizeBytes);
            FileMetadataService.save(dynamoDb, metadata);
            processed++;
        }
        return "Processed " + processed + " record(s)";
    }
}
