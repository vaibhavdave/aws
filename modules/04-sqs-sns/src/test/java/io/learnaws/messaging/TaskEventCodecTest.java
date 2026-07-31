package io.learnaws.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.learnaws.dynamodb.TaskStatus;

/** Pure JSON round-trip, no Floci required. */
class TaskEventCodecTest {

    @Test
    void roundTripsThroughJson() {
        TaskEvent event = new TaskEvent("task-1", "alice", TaskStatus.DONE, Instant.parse("2026-01-01T00:00:00Z"));

        String json = TaskEventCodec.toJson(event);
        TaskEvent decoded = TaskEventCodec.fromJson(json);

        assertThat(decoded).isEqualTo(event);
    }
}
