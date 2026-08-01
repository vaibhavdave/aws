package io.learnaws.cache;

import software.amazon.awssdk.services.elasticache.ElastiCacheClient;
import software.amazon.awssdk.services.elasticache.model.ReplicationGroup;
import software.amazon.awssdk.services.elasticache.model.ReplicationGroupAlreadyExistsException;

/**
 * Provisions a single-node Redis replication group - Floci runs ElastiCache as a real
 * Docker container (valkey/valkey:8 by default), speaking the standard Redis protocol, so
 * any Redis client (this module uses Jedis) works against it unmodified.
 *
 * Real AWS's CreateCacheCluster only ever provisions memcached; Redis/Valkey (single-node
 * or clustered) is always provisioned via CreateReplicationGroup, and Floci enforces that
 * same split - it rejects CreateCacheCluster with engine=redis.
 */
public final class ElastiCacheProvisioner {

    public static final String REPLICATION_GROUP_ID = "task-tracker-cache";
    private static final String DESCRIPTION = "Task tracker read-through cache";

    private ElastiCacheProvisioner() {
    }

    public record ConnectionInfo(String host, int port) {
    }

    public static ConnectionInfo provision(ElastiCacheClient elastiCache) {
        // Try-create-and-catch-the-conflict, the same idiom every other Admin class in this
        // repo uses (see TaskTableAdmin / RdsProvisioner) - a describe-first check doesn't
        // work here because Floci's DescribeReplicationGroups doesn't reject a never-created
        // identifier, so a describe-then-create-if-missing check silently skips creation.
        try {
            elastiCache.createReplicationGroup(b -> b
                    .replicationGroupId(REPLICATION_GROUP_ID)
                    .replicationGroupDescription(DESCRIPTION)
                    .engine("redis")
                    .cacheNodeType("cache.t3.micro")
                    .numCacheClusters(1));
        } catch (ReplicationGroupAlreadyExistsException alreadyExists) {
            // already provisioned on a previous run
        }

        elastiCache.waiter().waitUntilReplicationGroupAvailable(b -> b.replicationGroupId(REPLICATION_GROUP_ID));

        ReplicationGroup group = elastiCache.describeReplicationGroups(b -> b.replicationGroupId(REPLICATION_GROUP_ID))
                .replicationGroups()
                .get(0);

        var endpoint = group.nodeGroups().get(0).primaryEndpoint();
        return new ConnectionInfo(endpoint.address(), endpoint.port());
    }
}
