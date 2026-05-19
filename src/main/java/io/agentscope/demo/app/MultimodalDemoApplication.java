package io.agentscope.demo.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 入口：扫描 {@code io.agentscope.demo} 下所有组件（含 {@code app} 与示例包中的 Bean）。
 *
 * <p>{@link EnableScheduling} 启用 Spring {@code @Scheduled} 等调度能力；本 demo 中 {@link
 * io.agentscope.demo.app.service.FileJobService} 的模拟进度另使用自建 {@link
 * java.util.concurrent.ScheduledExecutorService}。
 */
@SpringBootApplication(scanBasePackages = "io.agentscope.demo")
@EnableScheduling
public class MultimodalDemoApplication {

    private static final Logger log = LoggerFactory.getLogger(MultimodalDemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MultimodalDemoApplication.class, args);
    }

    /** 启动完成后的单行锚点日志，便于在混合日志中检索本应用实例。 */
    @Bean
    ApplicationRunner multimodalBootLog(Environment env) {
        return args -> {
            String profiles = String.join(",", env.getActiveProfiles());
            if (profiles.isEmpty()) {
                profiles = "(default)";
            }
            log.info("[boot] multimodal-demo ready activeProfiles={}", profiles);
        };
    }
}
