package io.learnaws.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure logic, no Floci or network required. */
class StructuredLoggerTest {

    @Test
    void infoProducesAWellFormedJsonEventWithTheGivenFields() {
        StructuredLogger logger = new StructuredLogger("test-component");

        StructuredLogEvent event = logger.info("something happened", Map.of("taskId", "t1"));

        assertThat(event.level()).isEqualTo("INFO");
        assertThat(event.component()).isEqualTo("test-component");
        assertThat(event.message()).isEqualTo("something happened");
        assertThat(event.fields()).containsEntry("taskId", "t1");

        String json = event.toJson();
        assertThat(json).contains("\"level\":\"INFO\"").contains("\"taskId\":\"t1\"");
    }

    @Test
    void errorLevelIsCapturedDistinctlyFromInfo() {
        StructuredLogger logger = new StructuredLogger("test-component");

        StructuredLogEvent event = logger.error("it broke", null);

        assertThat(event.level()).isEqualTo("ERROR");
        assertThat(event.fields()).isEmpty();
    }
}
