package io.agentscope.demo.app.config;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行器：供 SSE 等「先返回连接、再在后台跑长任务」的场景使用，避免阻塞 Servlet 线程。
 *
 * <p>Bean 名 {@code agentscopeTaskExecutor} 与 {@link org.springframework.beans.factory.annotation.Qualifier}
 * 注入点一致。
 */
@Configuration
public class AgentscopeAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeAsyncConfig.class);

    /**
     * 有界线程池：用于 {@link io.agentscope.demo.app.web.FormVisionController} 在返回 {@link
     * org.springframework.web.servlet.mvc.method.annotation.SseEmitter} 后异步执行视觉分析。
     */
    @Bean(name = "agentscopeTaskExecutor")
    public Executor agentscopeTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("agentscope-sse-");
        ex.initialize();
        log.info(
                "[async] agentscopeTaskExecutor core={} max={} queue={} prefix={}",
                ex.getCorePoolSize(),
                ex.getMaxPoolSize(),
                ex.getQueueCapacity(),
                ex.getThreadNamePrefix());
        return ex;
    }
}
