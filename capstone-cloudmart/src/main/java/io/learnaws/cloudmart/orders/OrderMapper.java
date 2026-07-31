package io.learnaws.cloudmart.orders;

import java.time.Instant;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderItem toItem(Order order) {
        OrderItem item = new OrderItem();
        item.setId(order.id());
        item.setCustomerId(order.customerId());
        item.setProductId(order.productId());
        item.setQuantity(order.quantity());
        item.setTotalCents(order.totalCents());
        item.setStatus(order.status().name());
        item.setCreatedAt(order.createdAt().toString());
        return item;
    }

    public static Order toDomain(OrderItem item) {
        return new Order(
                item.getId(),
                item.getCustomerId(),
                item.getProductId(),
                item.getQuantity(),
                item.getTotalCents(),
                OrderStatus.valueOf(item.getStatus()),
                Instant.parse(item.getCreatedAt()));
    }
}
