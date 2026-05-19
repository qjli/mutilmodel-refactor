package io.agentscope.demo.app.config;

import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.RedisSession;
import io.agentscope.demo.app.session.LazyInitializingSession;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 {@link RedisSession} + Lettuce 的分布式会话（生产推荐）。
 *
 * <p>连接参数复用 {@code spring.data.redis.*}；AgentScope 状态键使用 {@code agentscope.session.key-prefix}。
 */
@Configuration
@ConditionalOnProperty(prefix = "agentscope.session", name = "store", havingValue = "redis")
public class AgentscopeRedisSessionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeRedisSessionConfiguration.class);

    /**
     * AgentScope 专用 Lettuce 客户端（与 Spring {@link RedisConnectionFactory} 可并存两条连接）。
     * {@code destroyMethod=shutdown} 在容器关闭时释放连接池。
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient agentscopeLettuceClient(RedisProperties springRedis) {
        RedisURI uri = buildRedisUri(springRedis);
        log.info("[agentscope-session] lettuce connect {} (db={})", uri.getHost(), uri.getDatabase());
        return RedisClient.create(uri);
    }

    @Bean(destroyMethod = "close")
    public Session agentscopeSession(RedisClient agentscopeLettuceClient, AgentscopeProperties properties) {
        String prefix = properties.normalizedKeyPrefix();
        log.info("[agentscope-session] store=redis keyPrefix={} (lazy connect)", prefix);
        return new LazyInitializingSession(
                () ->
                        RedisSession.builder()
                                .lettuceClient(agentscopeLettuceClient)
                                .keyPrefix(prefix)
                                .build());
    }

    /** 供 coverage 与健康检查使用；底层为 Spring Boot 默认 Lettuce 连接工厂。 */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** 将 {@code spring.data.redis.*} 映射为 Lettuce {@link RedisURI}（支持 URL、密码、SSL、超时）。 */
    private static RedisURI buildRedisUri(RedisProperties props) {
        // 优先 redis:// 或 rediss:// 完整 URL（云厂商常提供）
        if (props.getUrl() != null && !props.getUrl().isBlank()) {
            return RedisURI.create(props.getUrl());
        }
        String host = props.getHost() != null ? props.getHost() : "localhost";
        int port = props.getPort() > 0 ? props.getPort() : 6379;
        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port).withDatabase(props.getDatabase());
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            char[] pwd =
                    props.getPassword() != null ? props.getPassword().toCharArray() : new char[0];
            builder.withAuthentication(props.getUsername(), pwd);
        } else if (props.getPassword() != null && !props.getPassword().isBlank()) {
            builder.withPassword(props.getPassword().toCharArray());
        }
        if (props.getSsl() != null && props.getSsl().isEnabled()) {
            builder.withSsl(true);
        }
        if (props.getTimeout() != null) {
            builder.withTimeout(props.getTimeout());
        }
        return builder.build();
    }
}
