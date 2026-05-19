package io.agentscope.demo.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 全局配置：主要为本地 Vite 开发机（5173）访问 Spring（8888）时放开跨域。
 *
 * <p>生产环境请按实际前端域名收紧 {@link CorsRegistry#allowedOrigins}，避免过度放开。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许浏览器对 {@code /api/**} 发起跨域请求（含预检 OPTIONS）。 */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
