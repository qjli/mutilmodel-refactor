package io.agentscope.demo.app.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.demo.app.config.AgentscopeProperties;
import io.agentscope.demo.app.upload.MaterialSampleIds;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
     * 证照覆盖侧车：Redis STRING，键 {@code {keyPrefix}coverage:{sessionId}}，JSON 数组，TTL 90 天。
 *
 * <p>使用 Spring {@link StringRedisTemplate}，与 AgentScope {@link io.agentscope.core.session.redis.RedisSession}
 * 的 Lettuce 客户端分离。
 */
@Component
@ConditionalOnProperty(prefix = "agentscope.session", name = "store", havingValue = "redis")
public class RedisMaterialCoveragePersistence implements MaterialCoveragePersistence {

    private static final Logger log = LoggerFactory.getLogger(RedisMaterialCoveragePersistence.class);

    private final StringRedisTemplate redis;
    private final AgentscopeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisMaterialCoveragePersistence(
            StringRedisTemplate redis, AgentscopeProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Set<String> readMerged(String safeSessionId) {
        String raw = redis.opsForValue().get(coverageKey(safeSessionId));
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        try {
            List<String> ids =
                    objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            if (ids == null || ids.isEmpty()) {
                return Set.of();
            }
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String id : ids) {
                if (id != null && MaterialSampleIds.CANONICAL_ORDER.contains(id)) {
                    out.add(id);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[coverage] redis read failed sessionId={}", safeSessionId, e);
            return Set.of();
        }
    }

    @Override
    public void writeMerged(String safeSessionId, Set<String> detected) {
        try {
            String json = objectMapper.writeValueAsString(List.copyOf(detected));
            redis.opsForValue().set(coverageKey(safeSessionId), json, Duration.ofDays(90));
            log.debug("[coverage] redis write sessionId={} count={}", safeSessionId, detected.size());
        } catch (Exception e) {
            log.warn("[coverage] redis write failed sessionId={}", safeSessionId, e);
        }
    }

    /** 与 AgentScope 键同前缀、独立键名，避免与 {@code memory_messages:list} 冲突。 */
    private String coverageKey(String safeSessionId) {
        return properties.normalizedKeyPrefix() + "coverage:" + safeSessionId;
    }
}
