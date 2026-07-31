package io.learnaws.cloudmart.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrderEventCodecTest {

    @Test
    void roundTripsThroughJson() {
        OrderEvent event = new OrderEvent("o1", "alice", "p1", 2, 1198, Instant.parse("2026-01-01T00:00:00Z"));

        String json = OrderEventCodec.toJson(event);
        OrderEvent decoded = OrderEventCodec.fromJson(json);

        assertThat(decoded).isEqualTo(event);
    }
}
