package xin.v5ai.nb.starter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.starter.controller.vo.ApiKeyUsageStatsResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API Key 用量聚合服务。
 *
 * <p>模型用量明细暂未冗余 api_key_id，因此通过 run → conversation 追溯门户会话归属。
 * 管理端调试调用、历史非运行期调用等无法关联 API Key 的数据统一归入未关联分组。</p>
 */
@Service
@RequiredArgsConstructor
public class ApiKeyUsageStatsService {
    private static final String SUMMARY_SQL = """
            SELECT k.id AS api_key_id,
                   k.name AS api_key_name,
                   k.tracking_id,
                   COUNT(mu.id) AS model_calls,
                   COALESCE(SUM(mu.total_tokens), 0) AS total_tokens,
                   COALESCE(SUM(mu.prompt_tokens), 0) AS prompt_tokens,
                   COALESCE(SUM(mu.completion_tokens), 0) AS completion_tokens,
                   COALESCE(SUM(CASE WHEN UPPER(COALESCE(mu.status, '')) = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success_calls,
                   COALESCE(SUM(CASE WHEN UPPER(COALESCE(mu.status, '')) = 'SUCCESS' THEN 0 ELSE 1 END), 0) AS failed_calls,
                   COUNT(DISTINCT r.id) AS run_count,
                   COUNT(DISTINCT c.id) AS conversation_count,
                   COALESCE(AVG(mu.duration_ms), 0) AS average_duration_ms
            FROM v5ai_api_keys k
            LEFT JOIN v5ai_conversation c ON c.api_key_id = k.id
            LEFT JOIN v5ai_run r ON r.conversation_id = c.id
            LEFT JOIN v5ai_model_usage mu
                   ON mu.run_id = r.id
                  AND CAST(mu.created_at AS DATE) BETWEEN ? AND ?
            GROUP BY k.id, k.name, k.tracking_id
            UNION ALL
            SELECT NULL AS api_key_id,
                   '未关联 API Key' AS api_key_name,
                   NULL AS tracking_id,
                   COUNT(mu.id) AS model_calls,
                   COALESCE(SUM(mu.total_tokens), 0) AS total_tokens,
                   COALESCE(SUM(mu.prompt_tokens), 0) AS prompt_tokens,
                   COALESCE(SUM(mu.completion_tokens), 0) AS completion_tokens,
                   COALESCE(SUM(CASE WHEN UPPER(COALESCE(mu.status, '')) = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success_calls,
                   COALESCE(SUM(CASE WHEN UPPER(COALESCE(mu.status, '')) = 'SUCCESS' THEN 0 ELSE 1 END), 0) AS failed_calls,
                   COUNT(DISTINCT r.id) AS run_count,
                   COUNT(DISTINCT c.id) AS conversation_count,
                   COALESCE(AVG(mu.duration_ms), 0) AS average_duration_ms
            FROM v5ai_model_usage mu
            LEFT JOIN v5ai_run r ON r.id = mu.run_id
            LEFT JOIN v5ai_conversation c ON c.id = r.conversation_id
            WHERE CAST(mu.created_at AS DATE) BETWEEN ? AND ?
              AND c.api_key_id IS NULL
            ORDER BY total_tokens DESC, model_calls DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public ApiKeyUsageStatsResponse query(LocalDate from, LocalDate to, Long apiKeyId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("统计日期范围无效");
        }
        List<Map<String, Object>> summaryRows = jdbcTemplate.queryForList(SUMMARY_SQL, from, to, from, to);
        String dailySql = """
                SELECT CAST(mu.created_at AS DATE) AS usage_date,
                       COUNT(*) AS model_calls,
                       COALESCE(SUM(mu.total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(CASE WHEN UPPER(COALESCE(mu.status, '')) = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success_calls,
                       COALESCE(SUM(CASE WHEN UPPER(COALESCE(mu.status, '')) = 'SUCCESS' THEN 0 ELSE 1 END), 0) AS failed_calls
                FROM v5ai_model_usage mu
                LEFT JOIN v5ai_run r ON r.id = mu.run_id
                LEFT JOIN v5ai_conversation c ON c.id = r.conversation_id
                WHERE CAST(mu.created_at AS DATE) BETWEEN ? AND ?
                """ + (apiKeyId == null ? "" : " AND c.api_key_id = ?\n") + """
                GROUP BY CAST(mu.created_at AS DATE)
                ORDER BY usage_date
                """;
        List<Map<String, Object>> dailyRows = apiKeyId == null
                ? jdbcTemplate.queryForList(dailySql, from, to)
                : jdbcTemplate.queryForList(dailySql, from, to, apiKeyId);
        return new ApiKeyUsageStatsResponse(
                summaryRows.stream().map(this::toSummary).toList(),
                dailyRows.stream().map(this::toDaily).toList());
    }

    private ApiKeyUsageStatsResponse.Summary toSummary(Map<String, Object> row) {
        return new ApiKeyUsageStatsResponse.Summary(
                number(row.get("api_key_id")),
                string(row.get("api_key_name")),
                string(row.get("tracking_id")),
                number(row.get("model_calls"), 0L),
                number(row.get("total_tokens"), 0L),
                number(row.get("prompt_tokens"), 0L),
                number(row.get("completion_tokens"), 0L),
                number(row.get("success_calls"), 0L),
                number(row.get("failed_calls"), 0L),
                number(row.get("run_count"), 0L),
                number(row.get("conversation_count"), 0L),
                number(row.get("average_duration_ms"), 0L));
    }

    private ApiKeyUsageStatsResponse.Daily toDaily(Map<String, Object> row) {
        Object date = row.get("usage_date");
        LocalDate usageDate = date instanceof LocalDate localDate
                ? localDate
                : LocalDate.parse(String.valueOf(date));
        return new ApiKeyUsageStatsResponse.Daily(
                usageDate,
                number(row.get("model_calls"), 0L),
                number(row.get("total_tokens"), 0L),
                number(row.get("success_calls"), 0L),
                number(row.get("failed_calls"), 0L));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static long number(Object value, long defaultValue) {
        return value == null ? defaultValue : ((Number) value).longValue();
    }
}
