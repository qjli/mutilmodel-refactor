package io.agentscope.demo.app.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最小存活探测：供 K8s / 负载均衡或本地脚本检查应用是否已监听端口。
 */
@RestController
public class HealthController {

    /** 返回固定 JSON，不访问数据库或外部模型。 */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
