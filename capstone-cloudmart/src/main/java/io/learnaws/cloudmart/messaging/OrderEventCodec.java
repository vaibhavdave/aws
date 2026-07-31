package io.learnaws.cloudmart.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class OrderEventCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private OrderEventCodec() {
    }

    public static String toJson(OrderEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize " + event, e);
        }
    }

    public static OrderEvent fromJson(String json) {
        try {
            return MAPPER.readValue(json, OrderEvent.class);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to parse OrderEvent from: " + json, e);
        }
    }
}
