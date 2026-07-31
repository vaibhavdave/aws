package io.learnaws.api;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.IntegrationType;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;

/**
 * Builds a REST API over the Task Tracker table:
 *
 *   /tasks           POST (create), GET (list by owner query param)
 *   /tasks/{id}      GET, PATCH (update status), DELETE
 *
 * Every method uses AWS_PROXY integration to the same Lambda function - API Gateway just
 * hands it the raw request and returns whatever it responds with, unlike non-proxy
 * integrations which require request/response mapping templates.
 */
public final class ApiGatewayAdmin {

    private ApiGatewayAdmin() {
    }

    public record Resources(String restApiId, String invokeBaseUrl) {
    }

    public static Resources bootstrap(
            ApiGatewayClient apiGateway, LambdaClient lambda, String functionArn, Region region, String flociBaseUrl) {

        String restApiId = apiGateway.createRestApi(b -> b.name("task-tracker-api")).id();

        String rootResourceId = apiGateway.getResources(b -> b.restApiId(restApiId)).items().stream()
                .filter(r -> "/".equals(r.path()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("REST API has no root resource"))
                .id();

        String tasksResourceId = apiGateway.createResource(b -> b
                        .restApiId(restApiId).parentId(rootResourceId).pathPart("tasks"))
                .id();
        String taskIdResourceId = apiGateway.createResource(b -> b
                        .restApiId(restApiId).parentId(tasksResourceId).pathPart("{id}"))
                .id();

        String integrationUri = "arn:aws:apigateway:" + region.id()
                + ":lambda:path/2015-03-31/functions/" + functionArn + "/invocations";

        addProxyMethod(apiGateway, restApiId, tasksResourceId, "POST", integrationUri);
        addProxyMethod(apiGateway, restApiId, tasksResourceId, "GET", integrationUri);
        addProxyMethod(apiGateway, restApiId, taskIdResourceId, "GET", integrationUri);
        addProxyMethod(apiGateway, restApiId, taskIdResourceId, "PATCH", integrationUri);
        addProxyMethod(apiGateway, restApiId, taskIdResourceId, "DELETE", integrationUri);

        grantApiGatewayInvokePermission(lambda, functionArn, region, restApiId);

        apiGateway.createDeployment(b -> b.restApiId(restApiId).stageName("prod"));

        // LocalStack-style local invoke URL convention, which Floci mirrors for drop-in
        // compatibility: {base}/restapis/{restApiId}/{stage}/_user_request_{resourcePath}
        String invokeBaseUrl = flociBaseUrl + "/restapis/" + restApiId + "/prod/_user_request_";

        return new Resources(restApiId, invokeBaseUrl);
    }

    private static void addProxyMethod(
            ApiGatewayClient apiGateway, String restApiId, String resourceId, String httpMethod, String integrationUri) {
        apiGateway.putMethod(b -> b
                .restApiId(restApiId)
                .resourceId(resourceId)
                .httpMethod(httpMethod)
                .authorizationType("NONE"));

        apiGateway.putIntegration(b -> b
                .restApiId(restApiId)
                .resourceId(resourceId)
                .httpMethod(httpMethod)
                .type(IntegrationType.AWS_PROXY)
                .integrationHttpMethod("POST")
                .uri(integrationUri));
    }

    private static void grantApiGatewayInvokePermission(
            LambdaClient lambda, String functionArn, Region region, String restApiId) {
        String sourceArn = "arn:aws:execute-api:" + region.id() + ":000000000000:" + restApiId + "/*/*/*";
        try {
            lambda.addPermission(b -> b
                    .functionName(functionArn)
                    .statementId("allow-apigateway-invoke")
                    .action("lambda:InvokeFunction")
                    .principal("apigateway.amazonaws.com")
                    .sourceArn(sourceArn));
        } catch (ResourceConflictException alreadyGranted) {
            // already granted on a previous run
        }
    }
}
