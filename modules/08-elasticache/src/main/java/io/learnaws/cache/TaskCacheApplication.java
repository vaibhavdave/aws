package io.learnaws.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.learnaws.cache.ElastiCacheProvisioner.ConnectionInfo;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.elasticache.ElastiCacheClient;

/**
 * Run with: mvn -pl modules/08-elasticache spring-boot:run
 */
@SpringBootApplication
public class TaskCacheApplication {

    public static void main(String[] args) {
        try (ElastiCacheClient elastiCache = FlociEndpoint.local().configure(ElastiCacheClient.builder())) {
            ConnectionInfo cache = ElastiCacheProvisioner.provision(elastiCache);
            System.out.println("ElastiCache node available at " + cache.host() + ":" + cache.port());

            System.setProperty("elasticache.host", cache.host());
            System.setProperty("elasticache.port", String.valueOf(cache.port()));
        }

        SpringApplication.run(TaskCacheApplication.class, args);
    }
}
