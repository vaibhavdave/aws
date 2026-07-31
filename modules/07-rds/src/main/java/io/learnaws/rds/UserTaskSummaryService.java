package io.learnaws.rds;

import java.util.List;

import org.springframework.stereotype.Service;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;

/**
 * Polyglot persistence in one service: the user record lives in Postgres (relational,
 * this module), the task records live in DynamoDB (Module 03) - there's no SQL JOIN
 * possible across them, so the "join" happens in application code instead.
 */
@Service
public class UserTaskSummaryService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    public UserTaskSummaryService(UserRepository userRepository, TaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    public record UserTaskSummary(String username, String email, long totalTasks, long doneTasks) {
    }

    public UserTaskSummary summarize(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No user named " + username));

        List<Task> tasks = taskRepository.findByOwner(username);
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();

        return new UserTaskSummary(user.getUsername(), user.getEmail(), tasks.size(), done);
    }
}
