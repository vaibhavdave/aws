package io.learnaws.cloudmart;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.IntegrationType;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;

/** Same AWS_PROXY resource-tree pattern as Module 06's ApiGatewayAdmin, extended to two resource families. */
public final class CloudMartApiGatewayAdmin {

    private CloudMartApiGatewayAdmin() {
    }

    public record Resources(String restApiId, String invokeBaseUrl) {
    }

    public static Resources bootstrap(
            ApiGatewayClient apiGateway, LambdaClient lambda, String functionArn, Region region, String flociBaseUrl) {

        String restApiId = apiGateway.createRestApi(b -> b.name("cloudmart-api")).id();

        String rootResourceId = apiGateway.getResources(b -> b.restApiId(restApiId)).items().stream()
                .filter(r -> "/".equals(r.path()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("REST API has no root resource"))
                .id();

        String productsId = apiGateway.createResource(b -> b.restApiId(restApiId).parentId(rootResourceId).pathPart("products")).id();
        String productIdId = apiGateway.createResource(b -> b.restApiId(restApiId).parentId(productsId).pathPart("{id}")).id();
        String ordersId = apiGateway.createResource(b -> b.restApiId(restApiId).parentId(rootResourceId).pathPart("orders")).id();
        String orderIdId = apiGateway.createResource(b -> b.restApiId(restApiId).parentId(ordersId).pathPart("{id}")).id();

        String integrationUri = "arn:aws:apigateway:" + region.id()
                + ":lambda:path/2015-03-31/functions/" + functionArn + "/invocations";

        addProxyMethod(apiGateway, restApiId, productsId, "POST", integrationUri);
        addProxyMethod(apiGateway, restApiId, productsId, "GET", integrationUri);
        addProxyMethod(apiGateway, restApiId, productIdId, "GET", integrationUri);
        addProxyMethod(apiGateway, restApiId, ordersId, "POST", integrationUri);
        addProxyMethod(apiGateway, restApiId, ordersId, "GET", integrationUri);
        addProxyMethod(apiGateway, restApiId, orderIdId, "GET", integrationUri);

        grantApiGatewayInvokePermission(lambda, functionArn, region, restApiId);

        apiGateway.createDeployment(b -> b.restApiId(restApiId).stageName("prod"));

        String invokeBaseUrl = flociBaseUrl + "/restapis/" + restApiId + "/prod/_user_request_";
        return new Resources(restApiId, invokeBaseUrl);
    }

    private static void addProxyMethod(
            ApiGatewayClient apiGateway, String restApiId, String resourceId, String httpMethod, String integrationUri) {
        apiGateway.putMethod(b -> b.restApiId(restApiId).resourceId(resourceId).httpMethod(httpMethod).authorizationType("NONE"));
        apiGateway.putIntegration(b -> b
                .restApiId(restApiId)
                .resourceId(resourceId)
                .httpMethod(httpMethod)
                .type(IntegrationType.AWS_PROXY)
                .integrationHttpMethod("POST")
                .uri(integrationUri));
    }

    private static void grantApiGatewayInvokePermission(LambdaClient lambda, String functionArn, Region region, String restApiId) {
        String sourceArn = "arn:aws:execute-api:" + region.id() + ":000000000000:" + restApiId + "/*/*/*";
        try {
            lambda.addPermission(b -> b
                    .functionName(functionArn)
                    .statementId("allow-apigateway-invoke")
                    .action("lambda:InvokeFunction")
                    .principal("apigateway.amazonaws.com")
                    .sourceArn(sourceArn));
        } catch (ResourceConflictException alreadyGranted) {
            // already granted
        }
    }
}
