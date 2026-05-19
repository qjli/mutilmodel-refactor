package io.agentscope.demo.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * AgentScope 会话自动配置入口：按 {@code agentscope.session.store} 装配 file 或 redis 实现。
 */
@Configuration
@EnableConfigurationProperties(AgentscopeProperties.class)
@Import({AgentscopeFileSessionConfiguration.class, AgentscopeRedisSessionConfiguration.class})
public class AgentscopeSessionConfiguration {}
