package io.learnaws.cloudmart.messaging;

import java.time.Instant;

public record OrderEvent(String orderId, String customerId, String productId, int quantity, long totalCents, Instant occurredAt) {
}
