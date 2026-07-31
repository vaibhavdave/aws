package io.learnaws.cloudmart.processing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import io.learnaws.cloudmart.messaging.OrderEvent;
import io.learnaws.cloudmart.messaging.OrderEventCodec;
import io.learnaws.cloudmart.orders.OrderRepository;
import io.learnaws.cloudmart.orders.OrderStatus;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Triggered by the SQS event source mapping on cloudmart-order-processing-queue (wired by
 * CloudMartAdmin/CloudMartStack) - AWS polls the queue and invokes this function for you,
 * the automation Module 03's TaskStreamPoller and Module 04's QueueConsumer did by hand.
 * Confirms the order, i.e. moves it from PENDING to CONFIRMED.
 */
public class OrderProcessorHandler implements RequestHandler<SQSEvent, Void> {

    private final OrderRepository orderRepository;

    public OrderProcessorHandler() {
        DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        this.orderRepository = new OrderRepository(enhancedClient);
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSMessage message : event.getRecords()) {
            OrderEvent orderEvent = OrderEventCodec.fromJson(message.getBody());
            orderRepository.updateStatus(orderEvent.orderId(), OrderStatus.CONFIRMED);
        }
        return null;
    }
}
