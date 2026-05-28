package io.agentscope.demo.app.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS：本地 Vite（5173）跨域开发；Docker/Nginx 同端口访问时浏览器仍会带 Origin，需放行 ECS IP/域名。
 *
 * <p>经 Nginx 反代且前后端同域时，浏览器本可不依赖 CORS；但 Spring 一旦注册映射且 Origin 不在白名单会返回
 * {@code Invalid CORS request}。生产默认可用 {@code *} 模式（见 {@code app.cors.allowed-origin-patterns}）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value(
      "${app.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:*,http://127.0.0.1:*,*}")
  private String allowedOriginPatternsCsv;

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    String[] patterns =
        Arrays.stream(allowedOriginPatternsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    registry
        .addMapping("/api/**")
        .allowedOriginPatterns(patterns)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
  }
}
