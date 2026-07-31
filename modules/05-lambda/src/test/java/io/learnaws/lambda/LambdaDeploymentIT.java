package io.learnaws.lambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/**
 * Unlike this curriculum's other integration tests, this one does NOT spin up an ephemeral
 * Floci via Testcontainers: it deploys a real Lambda function that itself needs to reach
 * back into Floci over Docker networking (see LambdaDemo's AWS_ENDPOINT_URL note), which
 * only resolves predictably against the docker-compose-managed instance this repo ships.
 *
 * Prerequisites:
 *   docker compose up -d
 *   mvn -pl modules/05-lambda package
 * Then:
 *   mvn -pl modules/05-lambda verify -Pfloci
 *
 * Runs in the integration-test phase (via maven-failsafe-plugin), which is after `package` -
 * unlike this module's plain unit test, it needs target/lambda-file-processor.jar to already
 * exist, which `mvn test` alone would not have built yet.
 */
@Tag("floci")
class LambdaDeploymentIT {

    private static final String FUNCTION_NAME = "file-metadata-processor-it";
    private static final String BUCKET_NAME = "task-tracker-files";

    @Test
    void uploadingAFileTriggersTheLambdaWhichWritesMetadataToDynamoDb() {
        FlociEndpoint floci = FlociEndpoint.local();

        IamClient iam = floci.configure(IamClient.builder());
        LambdaClient lambda = floci.configure(LambdaClient.builder());
        S3Client s3 = S3Client.builder()
                .endpointOverride(floci.getEndpoint())
                .region(floci.getRegion())
                .credentialsProvider(floci.getCredentialsProvider())
                .forcePathStyle(true)
                .build();
        DynamoDbClient dynamoDb = floci.configure(DynamoDbClient.builder());

        LambdaIamBootstrap.BootstrapResult role = LambdaIamBootstrap.bootstrap(iam);
        FileMetadataTableAdmin.createTableIfNotExists(dynamoDb);

        try {
            s3.createBucket(b -> b.bucket(BUCKET_NAME));
        } catch (BucketAlreadyOwnedByYouException alreadyExists) {
            // fine
        }

        byte[] jarBytes = LambdaDeployer.readJar(Path.of("target", "lambda-file-processor.jar"));
        String functionArn = LambdaDeployer.deployOrUpdate(
                lambda, FUNCTION_NAME, "io.learnaws.lambda.FileMetadataHandler::handleRequest", jarBytes, role.roleArn(),
                Map.of("AWS_ENDPOINT_URL", "http://floci:4566"));

        S3NotificationWiring.wireBucketToLambda(s3, lambda, BUCKET_NAME, functionArn);

        String key = "lambda-it/" + java.util.UUID.randomUUID() + ".txt";
        s3.putObject(b -> b.bucket(BUCKET_NAME).key(key), RequestBody.fromString("trigger me"));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            GetItemResponse item = dynamoDb.getItem(b -> b
                    .tableName(FileMetadataTableAdmin.TABLE_NAME)
                    .key(Map.of(
                            "bucket", AttributeValue.fromS(BUCKET_NAME),
                            "key", AttributeValue.fromS(key))));

            assertThat(item.hasItem()).isTrue();
            assertThat(item.item().get("sizeBytes").n()).isEqualTo(String.valueOf("trigger me".getBytes().length));
        });
    }
}
