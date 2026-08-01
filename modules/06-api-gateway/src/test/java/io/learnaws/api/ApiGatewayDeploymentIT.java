package io.learnaws.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.learnaws.foundations.FlociEndpoint;
import io.learnaws.lambda.LambdaDeployer;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * Same rationale as Module 05's LambdaDeploymentIT: targets the docker-compose-managed
 * Floci instance directly (not an ephemeral Testcontainers one), since the deployed
 * function needs a predictable Docker-network route back to it.
 *
 * Prerequisites:
 *   docker compose up -d
 *   mvn -pl modules/06-api-gateway package
 * Then:
 *   mvn -pl modules/06-api-gateway verify -Pfloci
 */
@Tag("floci")
class ApiGatewayDeploymentIT {

    private static final String FUNCTION_NAME = "task-tracker-api-handler-it";

    @Test
    void fullCrudLifecycleThroughTheRealHttpApi() throws Exception {
        FlociEndpoint floci = FlociEndpoint.local();

        IamClient iam = floci.configure(IamClient.builder());
        LambdaClient lambda = floci.configure(LambdaClient.builder());
        ApiGatewayClient apiGateway = floci.configure(ApiGatewayClient.builder());

        ApiIamBootstrap.BootstrapResult role = ApiIamBootstrap.bootstrap(iam);
        byte[] jarBytes = LambdaDeployer.readJar(Path.of("target", "api-gateway-task-tracker.jar"));
        String functionArn = LambdaDeployer.deployOrUpdate(
                lambda, FUNCTION_NAME, "io.learnaws.api.TaskApiHandler::handleRequest", jarBytes, role.roleArn(),
                Map.of("AWS_ENDPOINT_URL", "http://floci:4566"));

        ApiGatewayAdmin.Resources api = ApiGatewayAdmin.bootstrap(
                apiGateway, lambda, functionArn, floci.getRegion(), floci.getEndpoint().toString());

        HttpClient http = HttpClient.newHttpClient();
        String owner = "owner-" + UUID.randomUUID();
        URI tasksUri = URI.create(api.invokeBaseUrl() + "/tasks");

        // The deployment API call returning doesn't guarantee the execute-plane has
        // finished wiring up the new stage yet - poll a side-effect-free GET until it
        // stops 404ing before running the real (state-changing) test flow below.
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            HttpResponse<String> probe = http.send(
                    HttpRequest.newBuilder(tasksUri).GET().build(), HttpResponse.BodyHandlers.ofString());
            return probe.statusCode() != 404;
        });

        HttpResponse<String> created = http.send(
                HttpRequest.newBuilder(tasksUri)
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(
                                "{\"title\":\"Integration test task\",\"description\":\"d\",\"owner\":\"" + owner + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        String id = TaskApiCodec.parseMap(created.body()).get("id").toString();
        URI taskUri = URI.create(api.invokeBaseUrl() + "/tasks/" + id);

        HttpResponse<String> listed = http.send(
                HttpRequest.newBuilder(URI.create(tasksUri + "?owner=" + owner)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains(id);

        HttpResponse<String> fetched = http.send(
                HttpRequest.newBuilder(taskUri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains("\"status\":\"TODO\"");

        HttpResponse<String> updated = http.send(
                HttpRequest.newBuilder(taskUri)
                        .header("Content-Type", "application/json")
                        .method("PATCH", BodyPublishers.ofString("{\"status\":\"DONE\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"status\":\"DONE\"");

        HttpResponse<String> deleted = http.send(
                HttpRequest.newBuilder(taskUri).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(deleted.statusCode()).isEqualTo(204);

        HttpResponse<String> goneNow = http.send(
                HttpRequest.newBuilder(taskUri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(goneNow.statusCode()).isEqualTo(404);
    }
}
