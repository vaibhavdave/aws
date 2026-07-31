package io.learnaws.cloudmart.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.learnaws.cloudmart.catalog.ProductRepository;
import io.learnaws.cloudmart.catalog.ProductTableAdmin;
import io.learnaws.cloudmart.messaging.OrderEventPublisher;
import io.learnaws.cloudmart.orders.OrderRepository;
import io.learnaws.cloudmart.orders.OrderTableAdmin;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * No explicit endpoint override anywhere here - see Module 05's note on why. This
 * function's environment carries AWS_ENDPOINT_URL, both when deployed by CloudMartAdmin
 * and by CloudMartStack (CDK).
 */
public class CloudMartApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String ORDER_TOPIC_ARN_ENV = "ORDER_TOPIC_ARN";

    private final CloudMartApiRouter router;

    public CloudMartApiHandler() {
        DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
        ProductTableAdmin.createTableIfNotExists(dynamoDb);
        OrderTableAdmin.createTableIfNotExists(dynamoDb);

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        ProductRepository productRepository = new ProductRepository(enhancedClient);
        OrderRepository orderRepository = new OrderRepository(enhancedClient);

        SnsClient sns = SnsClient.builder().build();
        String topicArn = System.getenv(ORDER_TOPIC_ARN_ENV);
        OrderEventPublisher publisher = new OrderEventPublisher(sns, topicArn);

        this.router = new CloudMartApiRouter(productRepository, orderRepository, publisher);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        return router.route(request);
    }
}
