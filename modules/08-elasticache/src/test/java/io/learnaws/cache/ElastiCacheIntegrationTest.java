package io.learnaws.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;
import io.learnaws.dynamodb.TaskTableAdmin;
import io.learnaws.foundations.FlociEndpoint;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.elasticache.ElastiCacheClient;

/**
 * Real Redis-protocol behavior against Floci's Docker-backed ElastiCache. Requires Docker.
 * Targets the docker-compose Floci instance directly (same reasoning as Modules 05-07).
 *
 * Run with: docker compose up -d, then mvn test -Pfloci -pl modules/08-elasticache
 * (First run pulls valkey/valkey:8, so provisioning takes longer than in-process services.)
 */
@Tag("floci")
class ElastiCacheIntegrationTest {

    @Test
    void secondReadForTheSameTaskIsServedFromCache() {
        FlociEndpoint floci = FlociEndpoint.local();

        ElastiCacheClient elastiCache = floci.configure(ElastiCacheClient.builder());
        ElastiCacheProvisioner.ConnectionInfo cache = ElastiCacheProvisioner.provision(elastiCache);
        UnifiedJedis redis = new UnifiedJedis(new HostAndPort(cache.host(), cache.port()));

        DynamoDbClient dynamoDb = floci.configure(DynamoDbClient.builder());
        TaskTableAdmin.createTableIfNotExists(dynamoDb);
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
        TaskRepository taskRepository = new TaskRepository(enhancedClient);

        CachedTaskService service = new CachedTaskService(taskRepository, redis);

        Task task = Task.newTask(UUID.randomUUID().toString(), "Cache me", "d", "alice");
        taskRepository.save(task);

        CachedTaskService.Lookup first = service.findById(task.id());
        assertThat(first.cacheHit()).isFalse();
        assertThat(first.task()).contains(task);

        CachedTaskService.Lookup second = service.findById(task.id());
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.task()).contains(task);

        service.updateStatus(task.id(), TaskStatus.DONE);

        CachedTaskService.Lookup afterUpdate = service.findById(task.id());
        assertThat(afterUpdate.cacheHit()).isFalse();
        assertThat(afterUpdate.task().get().status()).isEqualTo(TaskStatus.DONE);
    }
}
