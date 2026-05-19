package io.agentscope.demo.app.web;

import io.agentscope.demo.app.config.AgentscopeProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存活与依赖探测：供 K8s / 负载均衡或本地脚本检查。
 */
@RestController
public class HealthController {

    private final AgentscopeProperties agentscopeProperties;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    public HealthController(
            AgentscopeProperties agentscopeProperties,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        this.agentscopeProperties = agentscopeProperties;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("sessionStore", agentscopeProperties.getSession().getStore());
        if (agentscopeProperties.isRedisSessionStore()) {
            RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
            if (factory == null) {
                body.put("redis", "NOT_CONFIGURED");
                body.put("status", "DEGRADED");
            } else {
                try {
                    String pong = factory.getConnection().ping();
                    body.put("redis", pong != null ? "UP" : "UNKNOWN");
                } catch (Exception e) {
                    body.put("redis", "DOWN");
                    body.put("redisError", e.getClass().getSimpleName());
                    body.put("status", "DEGRADED");
                }
            }
        }
        return body;
    }
}
