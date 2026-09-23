package xin.v5ai.nb.starter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;
import xin.v5ai.nb.starter.controller.vo.ApiKeyUsageStatsResponse;
import xin.v5ai.nb.starter.controller.vo.OverviewResponse;
import xin.v5ai.nb.starter.service.ApiKeyUsageStatsService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 平台总览统计（可观测性）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
public class StatsController {
    private final JdbcTemplate jdbcTemplate;
    private final AgentService agentService;
    private final V5aiModelMapper modelMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SkillMapper skillMapper;
    private final ApiKeyUsageStatsService apiKeyUsageStatsService;

    /**
     * 获取平台总览统计。
     */
    @GetMapping("/overview")
    public R<OverviewResponse> overview() {
        // 直接传 LocalDate 对象：JDBC 映射为 date 类型，避免 "date = character varying" 报错
        var today = LocalDate.now();
        return R.ok(new OverviewResponse(
                agentService.list().size(),
                modelMapper.selectCount(null),
                knowledgeBaseMapper.selectCount(null),
                skillMapper.selectCount(null),
                count("SELECT count(*) FROM v5ai_run"),
                count("SELECT count(*) FROM v5ai_run WHERE CAST(started_at AS DATE) = ?", today),
                count("SELECT count(*) FROM v5ai_model_usage WHERE CAST(created_at AS DATE) = ?", today),
                count("SELECT COALESCE(SUM(total_tokens),0) FROM v5ai_model_usage WHERE CAST(created_at AS DATE) = ?", today)));
    }

    /**
     * 获取 API Key 维度的用量汇总和按日趋势。
     */
    @GetMapping("/api-key-usage")
    public R<ApiKeyUsageStatsResponse> apiKeyUsage(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long apiKeyId) {
        var end = to == null ? LocalDate.now() : to;
        var start = from == null ? end.minus(13, ChronoUnit.DAYS) : from;
        return R.ok(apiKeyUsageStatsService.query(start, end, apiKeyId));
    }

    /**
     * 执行 SQL 查询并返回结果。
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 结果
     */
    private long count(String sql, Object... args) {
        Long value = args == null || args.length == 0
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
