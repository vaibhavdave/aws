package io.learnaws.cache;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.learnaws.dynamodb.TaskStatus;

@RestController
@RequestMapping("/api/tasks")
public class CachedTaskController {

    public record UpdateStatusRequest(String status) {
    }

    private final CachedTaskService cachedTaskService;

    public CachedTaskController(CachedTaskService cachedTaskService) {
        this.cachedTaskService = cachedTaskService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        CachedTaskService.Lookup lookup = cachedTaskService.findById(id);

        if (lookup.task().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "task", lookup.task().get(),
                "cacheHit", lookup.cacheHit(),
                "elapsedMicros", lookup.elapsedNanos() / 1000));
    }

    @PatchMapping("/{id}")
    public Object updateStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        return cachedTaskService.updateStatus(id, TaskStatus.valueOf(request.status()));
    }
}
