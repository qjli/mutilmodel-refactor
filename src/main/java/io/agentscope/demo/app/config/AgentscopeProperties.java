package io.agentscope.demo.app.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 与 AgentScope 运行时相关的 Spring 配置（前缀 {@code agentscope.*}）。
 *
 * <p>当前仅承载会话持久化根目录；与 DashScope 密钥等无关。
 */
@ConfigurationProperties(prefix = "agentscope")
public class AgentscopeProperties {

    /** {@link io.agentscope.core.session.JsonSession} 在磁盘上的根目录（相对路径会基于进程工作目录解析）。 */
    private String sessionRoot = "data/agentscope-sessions";

    public String getSessionRoot() {
        return sessionRoot;
    }

    public void setSessionRoot(String sessionRoot) {
        this.sessionRoot = sessionRoot;
    }

    /** 规范化后的绝对路径，供创建目录与 {@link io.agentscope.core.session.JsonSession} 使用。 */
    public Path resolvedSessionRoot() {
        return Path.of(sessionRoot).toAbsolutePath().normalize();
    }
}
