package xin.v5ai.nb.starter.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.agentscope.core.repository.AgentRepository;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;

import java.time.LocalDate;

/**
 * 平台总览统计（可观测性）。
 */
@RestController
@RequestMapping("/api/admin/stats")
public class StatsController {
    private final JdbcTemplate jdbcTemplate;
    private final AgentRepository agentRepository;
    private final V5aiModelMapper modelMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SkillMapper skillMapper;

    public StatsController(JdbcTemplate jdbcTemplate,
                           AgentRepository agentRepository,
                           V5aiModelMapper modelMapper,
                           KnowledgeBaseMapper knowledgeBaseMapper,
                           SkillMapper skillMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.agentRepository = agentRepository;
        this.modelMapper = modelMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.skillMapper = skillMapper;
    }

    public record OverviewResponse(
            long agents,
            long models,
            long knowledgeBases,
            long skills,
            long totalRuns,
            long todayRuns,
            long todayModelCalls,
            long todayTokens
    ) {
    }

    @GetMapping("/overview")
    public R<OverviewResponse> overview() {
        // 直接传 LocalDate 对象：JDBC 映射为 date 类型，避免 "date = character varying" 报错
        var today = LocalDate.now();
        return R.ok(new OverviewResponse(
                agentRepository.list().size(),
                modelMapper.selectCount(null),
                knowledgeBaseMapper.selectCount(null),
                skillMapper.selectCount(null),
                count("SELECT count(*) FROM v5ai_run"),
                count("SELECT count(*) FROM v5ai_run WHERE started_at::date = ?", today),
                count("SELECT count(*) FROM v5ai_model_usage WHERE created_at::date = ?", today),
                count("SELECT COALESCE(SUM(total_tokens),0) FROM v5ai_model_usage WHERE created_at::date = ?", today)));
    }

    private long count(String sql, Object... args) {
        Long value = args == null || args.length == 0
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
