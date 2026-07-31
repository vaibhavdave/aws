package io.learnaws.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;
import redis.clients.jedis.UnifiedJedis;

/** Cache-aside logic tested with both DynamoDB and Redis mocked - no Floci, no Docker. */
class CachedTaskServiceTest {

    private TaskRepository taskRepository;
    private UnifiedJedis redis;
    private CachedTaskService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        redis = mock(UnifiedJedis.class);
        service = new CachedTaskService(taskRepository, redis);
    }

    @Test
    void cacheHitReturnsWithoutTouchingDynamoDb() {
        Task task = Task.newTask("t1", "Title", "d", "alice");
        when(redis.get("task:t1")).thenReturn(TaskCacheCodec.toJson(task));

        CachedTaskService.Lookup lookup = service.findById("t1");

        assertThat(lookup.cacheHit()).isTrue();
        assertThat(lookup.task()).contains(task);
        verify(taskRepository, never()).findById(any());
    }

    @Test
    void cacheMissFallsBackToDynamoDbAndPopulatesTheCache() {
        Task task = Task.newTask("t1", "Title", "d", "alice");
        when(redis.get("task:t1")).thenReturn(null);
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        CachedTaskService.Lookup lookup = service.findById("t1");

        assertThat(lookup.cacheHit()).isFalse();
        assertThat(lookup.task()).contains(task);
        verify(redis).setex(eq("task:t1"), anyLong(), org.mockito.ArgumentMatchers.<String>eq(TaskCacheCodec.toJson(task)));
    }

    @Test
    void cacheMissForAMissingTaskDoesNotPopulateTheCache() {
        when(redis.get("task:missing")).thenReturn(null);
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        CachedTaskService.Lookup lookup = service.findById("missing");

        assertThat(lookup.task()).isEmpty();
        verify(redis, never()).setex(org.mockito.ArgumentMatchers.<String>any(), anyLong(), org.mockito.ArgumentMatchers.<String>any());
    }

    @Test
    void updateStatusInvalidatesTheCacheEntry() {
        Task updated = Task.newTask("t1", "Title", "d", "alice").withStatus(TaskStatus.DONE);
        when(taskRepository.updateStatus("t1", TaskStatus.DONE)).thenReturn(updated);

        Task result = service.updateStatus("t1", TaskStatus.DONE);

        assertThat(result).isEqualTo(updated);
        verify(redis).del("task:t1");
    }
}
