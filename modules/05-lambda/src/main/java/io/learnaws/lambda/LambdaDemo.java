package io.learnaws.lambda;

import java.nio.file.Path;
import java.util.Map;

import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/**
 * Requires: mvn -pl modules/05-lambda package   (builds the deployment jar first)
 * Then run: mvn -pl modules/05-lambda compile exec:java
 *
 * Docker must be running - Floci launches a real Lambda runtime container for this function,
 * pulling public.ecr.aws/lambda/java21 the first time (may take a minute).
 */
public final class LambdaDemo {

    private static final String FUNCTION_NAME = "file-metadata-processor";
    private static final String BUCKET_NAME = "task-tracker-files";

    public static void main(String[] args) throws InterruptedException {
        FlociEndpoint floci = FlociEndpoint.local();

        try (IamClient iam = floci.configure(IamClient.builder());
                LambdaClient lambda = floci.configure(LambdaClient.builder());
                S3Client s3 = S3Client.builder()
                        .endpointOverride(floci.getEndpoint())
                        .region(floci.getRegion())
                        .credentialsProvider(floci.getCredentialsProvider())
                        .forcePathStyle(true)
                        .build();
                DynamoDbClient dynamoDb = floci.configure(DynamoDbClient.builder())) {

            LambdaIamBootstrap.BootstrapResult roleResult = LambdaIamBootstrap.bootstrap(iam);
            System.out.println("Lambda execution role: " + roleResult.roleArn());

            FileMetadataTableAdmin.createTableIfNotExists(dynamoDb);

            try {
                s3.createBucket(b -> b.bucket(BUCKET_NAME));
            } catch (BucketAlreadyOwnedByYouException alreadyExists) {
                // Module 02's File Vault may have already created it
            }

            byte[] jarBytes = LambdaDeployer.readJar(Path.of("target", "lambda-file-processor.jar"));

            // "floci" here is this repo's docker-compose service/container name for Floci -
            // the Lambda container Floci launches joins its network and can reach it there.
            // If your setup uses a different Floci hostname/network, adjust this.
            String functionArn = LambdaDeployer.deployOrUpdate(
                    lambda, FUNCTION_NAME, jarBytes, roleResult.roleArn(),
                    Map.of("AWS_ENDPOINT_URL", "http://floci:4566"));
            System.out.println("Deployed function: " + functionArn);

            S3NotificationWiring.wireBucketToLambda(s3, lambda, BUCKET_NAME, functionArn);
            System.out.println("Wired s3:ObjectCreated:* on " + BUCKET_NAME + " -> " + FUNCTION_NAME);

            String key = "lambda-demo/hello.txt";
            s3.putObject(b -> b.bucket(BUCKET_NAME).key(key), software.amazon.awssdk.core.sync.RequestBody.fromString("trigger me"));
            System.out.println("Uploaded s3://" + BUCKET_NAME + "/" + key + " - waiting for the Lambda to process it...");

            Thread.sleep(5000);

            GetItemResponse item = dynamoDb.getItem(b -> b
                    .tableName(FileMetadataTableAdmin.TABLE_NAME)
                    .key(Map.of(
                            "bucket", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(BUCKET_NAME),
                            "key", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(key))));

            if (item.hasItem()) {
                System.out.println("Metadata written by the Lambda: " + item.item());
            } else {
                System.out.println("No metadata found yet - the function may still be cold-starting; "
                        + "re-run the GetItem after a few more seconds.");
            }
        }
    }
}
