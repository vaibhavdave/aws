package io.learnaws.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;

@Configuration
public class RedisConfig {

    @Bean
    public UnifiedJedis redisClient(
            @Value("${elasticache.host}") String host, @Value("${elasticache.port}") int port) {
        return new UnifiedJedis(new HostAndPort(host, port));
    }
}
