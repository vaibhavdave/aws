package io.learnaws.rds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;
import io.learnaws.rds.UserTaskSummaryService.UserTaskSummary;

/**
 * Polyglot "join" logic tested with both repositories mocked - no Postgres, no DynamoDB,
 * no Floci, no Docker required.
 */
class UserTaskSummaryServiceTest {

    private UserRepository userRepository;
    private TaskRepository taskRepository;
    private UserTaskSummaryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        taskRepository = mock(TaskRepository.class);
        service = new UserTaskSummaryService(userRepository, taskRepository);
    }

    @Test
    void summarizesTotalAndDoneTaskCountsForAUser() {
        UserEntity user = new UserEntity(UUID.randomUUID(), "alice", "alice@example.com", Instant.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        List<Task> tasks = List.of(
                Task.newTask("1", "a", "d", "alice").withStatus(TaskStatus.DONE),
                Task.newTask("2", "b", "d", "alice").withStatus(TaskStatus.IN_PROGRESS),
                Task.newTask("3", "c", "d", "alice").withStatus(TaskStatus.DONE));
        when(taskRepository.findByOwner("alice")).thenReturn(tasks);

        UserTaskSummary summary = service.summarize("alice");

        assertThat(summary.username()).isEqualTo("alice");
        assertThat(summary.email()).isEqualTo("alice@example.com");
        assertThat(summary.totalTasks()).isEqualTo(3);
        assertThat(summary.doneTasks()).isEqualTo(2);
    }

    @Test
    void throwsWhenTheUserDoesNotExist() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarize("nobody")).isInstanceOf(IllegalArgumentException.class);
    }
}
