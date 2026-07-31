package io.learnaws.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskTableAdmin;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * The thin Lambda adapter - built once per warm execution environment, delegates every
 * request to {@link TaskApiRouter}. See Module 05 for why DynamoDbClient.builder().build()
 * with no explicit endpoint override is the right call here (it honors AWS_ENDPOINT_URL).
 */
public class TaskApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TaskApiRouter router;

    public TaskApiHandler() {
        DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
        TaskTableAdmin.createTableIfNotExists(dynamoDb);

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        this.router = new TaskApiRouter(new TaskRepository(enhancedClient));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        return router.route(request);
    }
}
