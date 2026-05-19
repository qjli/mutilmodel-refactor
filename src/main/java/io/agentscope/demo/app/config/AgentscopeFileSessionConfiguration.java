package io.agentscope.demo.app.config;

import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import java.io.IOException;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 磁盘 {@link JsonSession}（开发/无 Redis 环境）。 */
@Configuration
@ConditionalOnProperty(prefix = "agentscope.session", name = "store", havingValue = "file")
public class AgentscopeFileSessionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeFileSessionConfiguration.class);

    /** 每个 sessionId 对应子目录，内含 memory_messages.jsonl、agent_meta.json 等。 */
    @Bean(destroyMethod = "close")
    public Session agentscopeSession(AgentscopeProperties properties) throws IOException {
        var root = properties.resolvedSessionRoot();
        Files.createDirectories(root);
        log.info("[agentscope-session] store=file root={}", root);
        return new JsonSession(root);
    }
}
