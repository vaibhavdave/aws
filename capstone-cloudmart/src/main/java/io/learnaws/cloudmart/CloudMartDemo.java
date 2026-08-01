package io.learnaws.cloudmart;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import io.learnaws.cloudmart.api.CloudMartApiCodec;
import io.learnaws.cloudmart.catalog.ProductImageService;
import io.learnaws.cloudmart.catalog.ProductTableAdmin;
import io.learnaws.cloudmart.iam.CloudMartIamBootstrap;
import io.learnaws.cloudmart.messaging.MessagingAdmin;
import io.learnaws.cloudmart.orders.OrderTableAdmin;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * The full CloudMart bootstrap, imperatively - the same steps Modules 01-06 taught by hand,
 * applied together to a new domain. Compare this class to cdk/CloudMartStack, which
 * describes the identical infrastructure declaratively.
 *
 * Requires: mvn -pl capstone-cloudmart package   (builds target/cloudmart-lambda.jar)
 * Then run: docker compose up -d && mvn -pl capstone-cloudmart compile exec:java
 */
public final class CloudMartDemo {

    private static final String API_FUNCTION_NAME = "cloudmart-api-handler";
    private static final String PROCESSOR_FUNCTION_NAME = "cloudmart-order-processor";

    public static void main(String[] args) throws Exception {
        FlociEndpoint floci = FlociEndpoint.local();

        try (IamClient iam = floci.configure(IamClient.builder());
                LambdaClient lambda = floci.configure(LambdaClient.builder());
                ApiGatewayClient apiGateway = floci.configure(ApiGatewayClient.builder());
                SnsClient sns = floci.configure(SnsClient.builder());
                SqsClient sqs = floci.configure(SqsClient.builder());
                DynamoDbClient dynamoDb = floci.configure(DynamoDbClient.builder());
                S3Client s3 = S3Client.builder()
                        .endpointOverride(floci.getEndpoint())
                        .region(floci.getRegion())
                        .credentialsProvider(floci.getCredentialsProvider())
                        .forcePathStyle(true)
                        .build()) {

            CloudMartIamBootstrap.Roles roles = CloudMartIamBootstrap.bootstrap(iam);
            ProductTableAdmin.createTableIfNotExists(dynamoDb);
            OrderTableAdmin.createTableIfNotExists(dynamoDb);

            S3Presigner presigner = S3Presigner.builder()
                    .endpointOverride(floci.getEndpoint())
                    .region(floci.getRegion())
                    .credentialsProvider(floci.getCredentialsProvider())
                    .build();
            new ProductImageService(s3, presigner).ensureBucketExists();

            MessagingAdmin.Resources messaging = MessagingAdmin.bootstrap(sns, sqs);
            System.out.println("Order events topic: " + messaging.topicArn());

            byte[] jarBytes = CloudMartLambdaDeployer.readJar(Path.of("target", "cloudmart-lambda.jar"));

            String apiFunctionArn = CloudMartLambdaDeployer.deployOrUpdate(
                    lambda, API_FUNCTION_NAME, "io.learnaws.cloudmart.api.CloudMartApiHandler::handleRequest",
                    jarBytes, roles.apiRoleArn(),
                    Map.of("AWS_ENDPOINT_URL", "http://floci:4566", "ORDER_TOPIC_ARN", messaging.topicArn()));
            System.out.println("Deployed API function: " + apiFunctionArn);

            String processorFunctionArn = CloudMartLambdaDeployer.deployOrUpdate(
                    lambda, PROCESSOR_FUNCTION_NAME, "io.learnaws.cloudmart.processing.OrderProcessorHandler::handleRequest",
                    jarBytes, roles.processorRoleArn(),
                    Map.of("AWS_ENDPOINT_URL", "http://floci:4566"));
            System.out.println("Deployed order processor function: " + processorFunctionArn);

            CloudMartEventSourceWiring.wireQueueToFunction(lambda, messaging.queueArn(), processorFunctionArn);

            CloudMartApiGatewayAdmin.Resources api = CloudMartApiGatewayAdmin.bootstrap(
                    apiGateway, lambda, apiFunctionArn, floci.getRegion(), floci.getEndpoint().toString());
            System.out.println("REST API base URL: " + api.invokeBaseUrl());

            HttpClient http = HttpClient.newHttpClient();
            URI productsUri = URI.create(api.invokeBaseUrl() + "/products");

            // See Module 06's ApiGatewayDemo: the deployment call returning doesn't
            // guarantee the execute-plane has finished wiring up the new stage yet.
            for (int attempt = 0; attempt < 15; attempt++) {
                HttpResponse<String> probe = http.send(
                        HttpRequest.newBuilder(productsUri).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (probe.statusCode() != 404) {
                    break;
                }
                Thread.sleep(1000);
            }

            HttpResponse<String> product = http.send(
                    HttpRequest.newBuilder(URI.create(api.invokeBaseUrl() + "/products"))
                            .header("Content-Type", "application/json")
                            .POST(BodyPublishers.ofString("""
                                    {"name":"Floci Sticker Pack","description":"For your laptop","priceCents":599}"""))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("POST /products -> " + product.statusCode() + " " + product.body());
            String productId = CloudMartApiCodec.parseMap(product.body()).get("id").toString();

            HttpResponse<String> order = http.send(
                    HttpRequest.newBuilder(URI.create(api.invokeBaseUrl() + "/orders"))
                            .header("Content-Type", "application/json")
                            .POST(BodyPublishers.ofString(
                                    "{\"customerId\":\"alice\",\"productId\":\"" + productId + "\",\"quantity\":2}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("POST /orders -> " + order.statusCode() + " " + order.body());
            String orderId = CloudMartApiCodec.parseMap(order.body()).get("id").toString();

            System.out.println("Waiting for the order processor to confirm the order...");
            for (int attempt = 0; attempt < 10; attempt++) {
                Thread.sleep(2000);
                HttpResponse<String> fetched = http.send(
                        HttpRequest.newBuilder(URI.create(api.invokeBaseUrl() + "/orders/" + orderId)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                System.out.println("GET /orders/" + orderId + " -> " + fetched.body());
                if (fetched.body().contains("\"CONFIRMED\"")) {
                    System.out.println("Order confirmed by the SQS-triggered processor Lambda.");
                    break;
                }
            }
        }
    }
}
