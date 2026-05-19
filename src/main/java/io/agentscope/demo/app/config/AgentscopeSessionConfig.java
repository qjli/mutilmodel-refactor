package io.agentscope.demo.app.config;

import io.agentscope.core.session.JsonSession;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 AgentScope 会话存储：基于磁盘的 {@link JsonSession}。
 *
 * <p>启动时确保 {@link AgentscopeProperties#resolvedSessionRoot()} 目录存在，避免首次 {@code saveTo} 失败。
 */
@Configuration
@EnableConfigurationProperties(AgentscopeProperties.class)
public class AgentscopeSessionConfig {

    /** 单例 {@link JsonSession}，全应用共享同一持久化根路径。 */
    @Bean
    public JsonSession jsonSession(AgentscopeProperties properties) throws IOException {
        var root = properties.resolvedSessionRoot();
        Files.createDirectories(root);
        return new JsonSession(root);
    }
}
