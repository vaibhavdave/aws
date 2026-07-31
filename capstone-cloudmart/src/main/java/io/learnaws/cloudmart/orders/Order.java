package io.learnaws.cloudmart.orders;

import java.time.Instant;

public record Order(
        String id,
        String customerId,
        String productId,
        int quantity,
        long totalCents,
        OrderStatus status,
        Instant createdAt) {

    public static Order newOrder(String id, String customerId, String productId, int quantity, long totalCents) {
        return new Order(id, customerId, productId, quantity, totalCents, OrderStatus.PENDING, Instant.now());
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, customerId, productId, quantity, totalCents, newStatus, createdAt);
    }
}
