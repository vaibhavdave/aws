package io.learnaws.cloudmart.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderMapperTest {

    @Test
    void toItemAndBackToDomainRoundTrips() {
        Order order = Order.newOrder("o1", "alice", "p1", 2, 1198);

        OrderItem item = OrderMapper.toItem(order);
        assertThat(item.getId()).isEqualTo("o1");
        assertThat(item.getStatus()).isEqualTo("PENDING");

        assertThat(OrderMapper.toDomain(item)).isEqualTo(order);
    }

    @Test
    void withStatusUpdatesStatusOnly() {
        Order order = Order.newOrder("o1", "alice", "p1", 2, 1198);

        Order confirmed = order.withStatus(OrderStatus.CONFIRMED);

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.id()).isEqualTo(order.id());
        assertThat(confirmed.totalCents()).isEqualTo(order.totalCents());
    }
}
