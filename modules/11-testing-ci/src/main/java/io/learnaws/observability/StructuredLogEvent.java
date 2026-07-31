package io.learnaws.observability;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * A single structured (JSON) log line - the shape that makes CloudWatch Logs Insights
 * queries like {@code fields message | filter level = "ERROR"} possible, versus a plain
 * unstructured text line that only supports substring search.
 */
public record StructuredLogEvent(Instant timestamp, String level, String component, String message, Map<String, Object> fields) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize log event", e);
        }
    }
}
