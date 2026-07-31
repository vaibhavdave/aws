package io.learnaws.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class TaskEventCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private TaskEventCodec() {
    }

    public static String toJson(TaskEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize " + event, e);
        }
    }

    public static TaskEvent fromJson(String json) {
        try {
            return MAPPER.readValue(json, TaskEvent.class);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to parse TaskEvent from: " + json, e);
        }
    }
}
