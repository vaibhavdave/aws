package io.learnaws.lambda;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Event;
import software.amazon.awssdk.services.s3.model.LambdaFunctionConfiguration;
import software.amazon.awssdk.services.s3.model.NotificationConfiguration;

/** Wires "PutObject into this bucket" to "invoke this Lambda function". */
public final class S3NotificationWiring {

    private S3NotificationWiring() {
    }

    public static void wireBucketToLambda(S3Client s3, LambdaClient lambda, String bucketName, String functionArn) {
        String bucketArn = "arn:aws:s3:::" + bucketName;

        try {
            lambda.addPermission(b -> b
                    .functionName(functionArn)
                    .statementId("allow-s3-" + bucketName)
                    .action("lambda:InvokeFunction")
                    .principal("s3.amazonaws.com")
                    .sourceArn(bucketArn));
        } catch (ResourceConflictException alreadyGranted) {
            // permission already granted from a previous run
        }

        s3.putBucketNotificationConfiguration(b -> b
                .bucket(bucketName)
                .notificationConfiguration(NotificationConfiguration.builder()
                        .lambdaFunctionConfigurations(LambdaFunctionConfiguration.builder()
                                .lambdaFunctionArn(functionArn)
                                .events(Event.S3_OBJECT_CREATED)
                                .build())
                        .build()));
    }
}
