package io.learnaws.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.learnaws.dynamodb.Task;

public final class TaskCacheCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private TaskCacheCodec() {
    }

    public static String toJson(Task task) {
        try {
            return MAPPER.writeValueAsString(task);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize " + task, e);
        }
    }

    public static Task fromJson(String json) {
        try {
            return MAPPER.readValue(json, Task.class);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to parse Task from: " + json, e);
        }
    }
}
