package io.learnaws.ecs;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;

@RestController
public class TaskController {

    public record CreateTaskRequest(String title, String description, String owner) {
    }

    public record UpdateStatusRequest(String status) {
    }

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /** ECS uses this for container health checks - see the Dockerfile's HEALTHCHECK. */
    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/tasks")
    public Task create(@RequestBody CreateTaskRequest request) {
        Task task = Task.newTask(UUID.randomUUID().toString(), request.title(), request.description(), request.owner());
        return taskRepository.save(task);
    }

    @GetMapping("/tasks")
    public List<Task> listByOwner(@RequestParam String owner) {
        return taskRepository.findByOwner(owner);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getById(@PathVariable String id) {
        return taskRepository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/tasks/{id}")
    public Task updateStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        return taskRepository.updateStatus(id, TaskStatus.valueOf(request.status()));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        taskRepository.delete(id);
        return ResponseEntity.noContent().build();
    }
}
