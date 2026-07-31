package io.learnaws.cloudmart.api;

import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.learnaws.cloudmart.catalog.Product;
import io.learnaws.cloudmart.catalog.ProductRepository;
import io.learnaws.cloudmart.messaging.OrderEvent;
import io.learnaws.cloudmart.messaging.OrderEventPublisher;
import io.learnaws.cloudmart.orders.Order;
import io.learnaws.cloudmart.orders.OrderRepository;

/**
 * All request handling for CloudMart's public API - kept separate from CloudMartApiHandler
 * so it's unit-testable with mocked repositories/publisher, the same split every prior
 * Lambda-backed module (05, 06, 09) used.
 */
public class CloudMartApiRouter {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public CloudMartApiRouter(
            ProductRepository productRepository, OrderRepository orderRepository, OrderEventPublisher orderEventPublisher) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    public APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent request) {
        String resource = request.getResource();
        String method = request.getHttpMethod();

        try {
            if ("/products".equals(resource) && "POST".equals(method)) {
                return createProduct(request);
            }
            if ("/products".equals(resource) && "GET".equals(method)) {
                return response(200, productRepository.list());
            }
            if ("/products/{id}".equals(resource) && "GET".equals(method)) {
                String id = request.getPathParameters().get("id");
                return productRepository.findById(id)
                        .map(p -> response(200, p))
                        .orElseGet(() -> response(404, Map.of("message", "no product with id " + id)));
            }
            if ("/orders".equals(resource) && "POST".equals(method)) {
                return createOrder(request);
            }
            if ("/orders".equals(resource) && "GET".equals(method)) {
                Map<String, String> query = request.getQueryStringParameters();
                String customerId = query == null ? null : query.get("customerId");
                if (customerId == null || customerId.isBlank()) {
                    return response(400, Map.of("message", "?customerId= query parameter is required"));
                }
                return response(200, orderRepository.findByCustomer(customerId));
            }
            if ("/orders/{id}".equals(resource) && "GET".equals(method)) {
                String id = request.getPathParameters().get("id");
                return orderRepository.findById(id)
                        .map(o -> response(200, o))
                        .orElseGet(() -> response(404, Map.of("message", "no order with id " + id)));
            }
            return response(404, Map.of("message", "no route for " + method + " " + resource));
        } catch (IllegalArgumentException badInput) {
            return response(400, Map.of("message", badInput.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent createProduct(APIGatewayProxyRequestEvent request) {
        Map<String, Object> body = CloudMartApiCodec.parseMap(request.getBody());
        String name = requireString(body, "name");
        String description = body.getOrDefault("description", "").toString();
        long priceCents = ((Number) requireNonNull(body, "priceCents")).longValue();
        String imageKey = (String) body.get("imageKey");

        Product product = new Product(UUID.randomUUID().toString(), name, description, priceCents, imageKey);
        productRepository.save(product);
        return response(201, product);
    }

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent request) {
        Map<String, Object> body = CloudMartApiCodec.parseMap(request.getBody());
        String customerId = requireString(body, "customerId");
        String productId = requireString(body, "productId");
        int quantity = ((Number) requireNonNull(body, "quantity")).intValue();
        if (quantity <= 0) {
            throw new IllegalArgumentException("\"quantity\" must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("No product with id " + productId));

        long totalCents = product.priceCents() * quantity;
        Order order = Order.newOrder(UUID.randomUUID().toString(), customerId, productId, quantity, totalCents);
        orderRepository.save(order);

        orderEventPublisher.publish(new OrderEvent(
                order.id(), order.customerId(), order.productId(), order.quantity(), order.totalCents(), order.createdAt()));

        return response(201, order);
    }

    private static String requireString(Map<String, Object> body, String field) {
        Object value = requireNonNull(body, field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("\"" + field + "\" must be a non-blank string");
        }
        return s;
    }

    private static Object requireNonNull(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException("\"" + field + "\" is required");
        }
        return value;
    }

    private static APIGatewayProxyResponseEvent response(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(CloudMartApiCodec.toJson(body));
    }
}
