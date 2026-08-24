package xin.v5ai.nb.starter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查端点（可观测性）：进程 + 数据库探活。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    /**
     * 健康检查。
     */
    @GetMapping
    public Map<String, Object> health() {
        boolean dbUp = isDatabaseUp();
        return Map.of(
                "status", dbUp ? "UP" : "DEGRADED",
                "database", dbUp ? "UP" : "DOWN",
                "service", "v5ai-nb");
    }

    private boolean isDatabaseUp() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception exception) {
            return false;
        }
    }
}
