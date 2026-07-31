package io.learnaws.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import io.learnaws.foundations.FlociEndpoint;
import io.learnaws.lambda.LambdaDeployer;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * Requires: mvn -pl modules/06-api-gateway package
 * Then run: mvn -pl modules/06-api-gateway compile exec:java
 */
public final class ApiGatewayDemo {

    private static final String FUNCTION_NAME = "task-tracker-api-handler";

    public static void main(String[] args) throws Exception {
        FlociEndpoint floci = FlociEndpoint.local();

        try (IamClient iam = floci.configure(IamClient.builder());
                LambdaClient lambda = floci.configure(LambdaClient.builder());
                ApiGatewayClient apiGateway = floci.configure(ApiGatewayClient.builder())) {

            ApiIamBootstrap.BootstrapResult role = ApiIamBootstrap.bootstrap(iam);

            byte[] jarBytes = LambdaDeployer.readJar(Path.of("target", "api-gateway-task-tracker.jar"));
            String functionArn = LambdaDeployer.deployOrUpdate(
                    lambda, FUNCTION_NAME, "io.learnaws.api.TaskApiHandler::handleRequest", jarBytes, role.roleArn(),
                    Map.of("AWS_ENDPOINT_URL", "http://floci:4566"));
            System.out.println("Deployed function: " + functionArn);

            ApiGatewayAdmin.Resources api = ApiGatewayAdmin.bootstrap(
                    apiGateway, lambda, functionArn, floci.getRegion(), floci.getEndpoint().toString());
            System.out.println("REST API base URL: " + api.invokeBaseUrl());

            HttpClient http = HttpClient.newHttpClient();
            URI tasksUri = URI.create(api.invokeBaseUrl() + "/tasks");

            HttpResponse<String> created = http.send(
                    HttpRequest.newBuilder(tasksUri)
                            .header("Content-Type", "application/json")
                            .POST(BodyPublishers.ofString("""
                                    {"title":"Write module 06 README","description":"API Gateway theory","owner":"alice"}"""))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("POST /tasks -> " + created.statusCode() + " " + created.body());

            String id = TaskApiCodec.parseMap(created.body()).get("id").toString();
            URI taskUri = URI.create(api.invokeBaseUrl() + "/tasks/" + id);

            HttpResponse<String> listed = http.send(
                    HttpRequest.newBuilder(URI.create(tasksUri + "?owner=alice")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("GET /tasks?owner=alice -> " + listed.statusCode() + " " + listed.body());

            HttpResponse<String> fetched = http.send(
                    HttpRequest.newBuilder(taskUri).GET().build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("GET /tasks/" + id + " -> " + fetched.statusCode() + " " + fetched.body());

            HttpResponse<String> updated = http.send(
                    HttpRequest.newBuilder(taskUri)
                            .header("Content-Type", "application/json")
                            .method("PATCH", BodyPublishers.ofString("""
                                    {"status":"DONE"}"""))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("PATCH /tasks/" + id + " -> " + updated.statusCode() + " " + updated.body());

            HttpResponse<String> deleted = http.send(
                    HttpRequest.newBuilder(taskUri).DELETE().build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("DELETE /tasks/" + id + " -> " + deleted.statusCode());
        }
    }
}
