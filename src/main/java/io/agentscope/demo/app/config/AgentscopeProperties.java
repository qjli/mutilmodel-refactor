package io.agentscope.demo.app.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentScope 运行时配置（前缀 {@code agentscope.*}）。
 *
 * <p>会话持久化支持 {@code file}（{@link io.agentscope.core.session.JsonSession}）与
 * {@code redis}（{@link io.agentscope.core.session.redis.RedisSession}）。
 */
@ConfigurationProperties(prefix = "agentscope")
public class AgentscopeProperties {

    private SessionStore session = new SessionStore();

    public SessionStore getSession() {
        return session;
    }

    public void setSession(SessionStore session) {
        this.session = session != null ? session : new SessionStore();
    }

    public Path resolvedSessionRoot() {
        return Path.of(session.getFileRoot()).toAbsolutePath().normalize();
    }

    public String normalizedKeyPrefix() {
        String p = session.getKeyPrefix();
        if (p == null || p.isBlank()) {
            p = "agentscope:multimodal-demo:";
        }
        p = p.trim();
        return p.endsWith(":") ? p : p + ":";
    }

    public boolean isRedisSessionStore() {
        return "redis".equalsIgnoreCase(session.getStore());
    }

    public boolean isFileSessionStore() {
        return !isRedisSessionStore();
    }

    public static class SessionStore {

        private String store = "redis";
        private String keyPrefix = "agentscope:multimodal-demo:";
        private String fileRoot = "data/agentscope-sessions";

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getFileRoot() {
            return fileRoot;
        }

        public void setFileRoot(String fileRoot) {
            this.fileRoot = fileRoot;
        }
    }
}
