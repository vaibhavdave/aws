package io.learnaws.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;
import redis.clients.jedis.UnifiedJedis;

/**
 * The cache-aside pattern, spelled out explicitly rather than hidden behind an annotation:
 *
 *   read:  check the cache -> hit? return it.
 *          miss? read the source of truth (DynamoDB), populate the cache, then return it.
 *   write: update the source of truth, then invalidate (not update) the cache entry -
 *          the next read repopulates it. Invalidating is simpler and safer than trying to
 *          keep a cached copy in sync with every write path.
 */
@Service
public class CachedTaskService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "task:";

    private final TaskRepository taskRepository;
    private final UnifiedJedis redis;

    public CachedTaskService(TaskRepository taskRepository, UnifiedJedis redis) {
        this.taskRepository = taskRepository;
        this.redis = redis;
    }

    public record Lookup(Optional<Task> task, boolean cacheHit, long elapsedNanos) {
    }

    public Lookup findById(String id) {
        long start = System.nanoTime();
        String cacheKey = KEY_PREFIX + id;

        String cached = redis.get(cacheKey);
        if (cached != null) {
            return new Lookup(Optional.of(TaskCacheCodec.fromJson(cached)), true, System.nanoTime() - start);
        }

        Optional<Task> task = taskRepository.findById(id);
        task.ifPresent(t -> redis.setex(cacheKey, TTL.toSeconds(), TaskCacheCodec.toJson(t)));

        return new Lookup(task, false, System.nanoTime() - start);
    }

    public Task updateStatus(String id, TaskStatus status) {
        Task updated = taskRepository.updateStatus(id, status);
        redis.del(KEY_PREFIX + id);
        return updated;
    }
}
