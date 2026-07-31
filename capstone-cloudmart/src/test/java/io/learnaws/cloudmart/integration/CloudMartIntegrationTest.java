package io.learnaws.cloudmart.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.cloudmart.catalog.Product;
import io.learnaws.cloudmart.catalog.ProductRepository;
import io.learnaws.cloudmart.catalog.ProductTableAdmin;
import io.learnaws.cloudmart.messaging.MessagingAdmin;
import io.learnaws.cloudmart.messaging.OrderEvent;
import io.learnaws.cloudmart.messaging.OrderEventCodec;
import io.learnaws.cloudmart.messaging.OrderEventPublisher;
import io.learnaws.cloudmart.orders.Order;
import io.learnaws.cloudmart.orders.OrderRepository;
import io.learnaws.cloudmart.orders.OrderTableAdmin;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Exercises the data + messaging layer directly against real Floci-backed DynamoDB, SNS,
 * and SQS - all in-process services, so (unlike Modules 05/06/09's deployed-Lambda tests)
 * an ephemeral Testcontainers Floci works fine here. Deploying CloudMartApiHandler and
 * OrderProcessorHandler for a fully end-to-end run follows exactly Module 06's
 * ApiGatewayDeploymentIT pattern - left as an exercise, not duplicated here.
 *
 * Requires Docker. Run with: mvn test -Pfloci -pl capstone-cloudmart
 */
@Tag("floci")
@Testcontainers
class CloudMartIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void creatingAnOrderComputesTotalAndDeliversAnEventToTheProcessingQueue() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        DynamoDbClient dynamoDb = endpoint.configure(DynamoDbClient.builder());
        ProductTableAdmin.createTableIfNotExists(dynamoDb);
        OrderTableAdmin.createTableIfNotExists(dynamoDb);
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        ProductRepository productRepository = new ProductRepository(enhancedClient);
        OrderRepository orderRepository = new OrderRepository(enhancedClient);

        SnsClient sns = endpoint.configure(SnsClient.builder());
        SqsClient sqs = endpoint.configure(SqsClient.builder());
        MessagingAdmin.Resources messaging = MessagingAdmin.bootstrap(sns, sqs);
        OrderEventPublisher publisher = new OrderEventPublisher(sns, messaging.topicArn());

        Product product = new Product(UUID.randomUUID().toString(), "Sticker Pack", "Floci stickers", 599, null);
        productRepository.save(product);

        Order order = Order.newOrder(UUID.randomUUID().toString(), "alice", product.id(), 3, product.priceCents() * 3);
        orderRepository.save(order);
        publisher.publish(new OrderEvent(
                order.id(), order.customerId(), order.productId(), order.quantity(), order.totalCents(), order.createdAt()));

        assertThat(orderRepository.findById(order.id())).get().extracting(Order::totalCents).isEqualTo(1797L);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<software.amazon.awssdk.services.sqs.model.Message> messages = sqs.receiveMessage(b -> b
                            .queueUrl(messaging.queueUrl())
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(1))
                    .messages();

            assertThat(messages).isNotEmpty();
            OrderEvent delivered = OrderEventCodec.fromJson(messages.get(0).body());
            assertThat(delivered.orderId()).isEqualTo(order.id());
            assertThat(delivered.totalCents()).isEqualTo(1797L);
        });
    }
}
