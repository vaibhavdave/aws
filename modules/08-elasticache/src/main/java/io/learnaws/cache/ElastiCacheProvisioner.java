package io.learnaws.cache;

import software.amazon.awssdk.services.elasticache.ElastiCacheClient;
import software.amazon.awssdk.services.elasticache.model.CacheCluster;
import software.amazon.awssdk.services.elasticache.model.CacheClusterNotFoundException;

/**
 * Provisions a single-node cache cluster - Floci runs ElastiCache as a real Docker
 * container (valkey/valkey:8 by default), speaking the standard Redis protocol, so any
 * Redis client (this module uses Jedis) works against it unmodified.
 */
public final class ElastiCacheProvisioner {

    public static final String CLUSTER_ID = "task-tracker-cache";

    private ElastiCacheProvisioner() {
    }

    public record ConnectionInfo(String host, int port) {
    }

    public static ConnectionInfo provision(ElastiCacheClient elastiCache) {
        if (!clusterExists(elastiCache)) {
            elastiCache.createCacheCluster(b -> b
                    .cacheClusterId(CLUSTER_ID)
                    .engine("redis")
                    .cacheNodeType("cache.t3.micro")
                    .numCacheNodes(1));
        }

        elastiCache.waiter().waitUntilCacheClusterAvailable(b -> b.cacheClusterId(CLUSTER_ID));

        CacheCluster cluster = elastiCache.describeCacheClusters(b -> b
                        .cacheClusterId(CLUSTER_ID)
                        .showCacheNodeInfo(true))
                .cacheClusters()
                .get(0);

        var endpoint = cluster.cacheNodes().get(0).endpoint();
        return new ConnectionInfo(endpoint.address(), endpoint.port());
    }

    private static boolean clusterExists(ElastiCacheClient elastiCache) {
        try {
            elastiCache.describeCacheClusters(b -> b.cacheClusterId(CLUSTER_ID));
            return true;
        } catch (CacheClusterNotFoundException notFound) {
            return false;
        }
    }
}
