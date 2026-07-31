package io.learnaws.cloudmart.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.learnaws.cloudmart.catalog.Product;
import io.learnaws.cloudmart.catalog.ProductRepository;
import io.learnaws.cloudmart.messaging.OrderEvent;
import io.learnaws.cloudmart.messaging.OrderEventPublisher;
import io.learnaws.cloudmart.orders.Order;
import io.learnaws.cloudmart.orders.OrderRepository;

class CloudMartApiRouterTest {

    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private OrderEventPublisher orderEventPublisher;
    private CloudMartApiRouter router;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        router = new CloudMartApiRouter(productRepository, orderRepository, orderEventPublisher);
    }

    private static APIGatewayProxyRequestEvent request(String method, String resource, Map<String, String> pathParams,
            Map<String, String> queryParams, String body) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withResource(resource)
                .withPathParameters(pathParams)
                .withQueryStringParameters(queryParams)
                .withBody(body);
    }

    @Test
    void postProductsCreatesAProductAndReturns201() {
        APIGatewayProxyResponseEvent response = router.route(request("POST", "/products", null, null, """
                {"name":"Sticker Pack","description":"d","priceCents":599}"""));

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody()).contains("\"name\":\"Sticker Pack\"").contains("\"priceCents\":599");
    }

    @Test
    void getProductsListsEveryProduct() {
        when(productRepository.list()).thenReturn(List.of(new Product("p1", "Sticker Pack", "d", 599, null)));

        APIGatewayProxyResponseEvent response = router.route(request("GET", "/products", null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"id\":\"p1\"");
    }

    @Test
    void getProductByIdReturns404WhenMissing() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        APIGatewayProxyResponseEvent response = router.route(request("GET", "/products/{id}", Map.of("id", "missing"), null, null));

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void postOrdersComputesTotalFromProductPriceAndPublishesAnEvent() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(new Product("p1", "Sticker Pack", "d", 599, null)));

        APIGatewayProxyResponseEvent response = router.route(request("POST", "/orders", null, null, """
                {"customerId":"alice","productId":"p1","quantity":3}"""));

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody()).contains("\"totalCents\":1797").contains("\"status\":\"PENDING\"");
        verify(orderEventPublisher).publish(any(OrderEvent.class));
    }

    @Test
    void postOrdersForAnUnknownProductReturns400AndDoesNotPublish() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        APIGatewayProxyResponseEvent response = router.route(request("POST", "/orders", null, null, """
                {"customerId":"alice","productId":"missing","quantity":1}"""));

        assertThat(response.getStatusCode()).isEqualTo(400);
        verify(orderEventPublisher, never()).publish(any());
    }

    @Test
    void getOrdersWithoutCustomerIdReturns400() {
        APIGatewayProxyResponseEvent response = router.route(request("GET", "/orders", null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void getOrdersWithCustomerIdReturnsThatCustomersOrders() {
        Order order = Order.newOrder("o1", "alice", "p1", 1, 599);
        when(orderRepository.findByCustomer("alice")).thenReturn(List.of(order));

        APIGatewayProxyResponseEvent response = router.route(
                request("GET", "/orders", null, Map.of("customerId", "alice"), null));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"id\":\"o1\"");
    }

    @Test
    void unknownRouteReturns404() {
        APIGatewayProxyResponseEvent response = router.route(request("PUT", "/unknown", null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(404);
    }
}
