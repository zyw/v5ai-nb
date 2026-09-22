package xin.v5ai.nb.starter.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyUsageStatsServiceTest {

    @Test
    void returnsSummaryAndDailyUsageForSelectedApiKey() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> summaryRow = new HashMap<>();
        summaryRow.put("api_key_id", 7L);
        summaryRow.put("api_key_name", "生产 Key");
        summaryRow.put("tracking_id", "track-7");
        summaryRow.put("model_calls", 12L);
        summaryRow.put("total_tokens", 3456L);
        summaryRow.put("prompt_tokens", 2100L);
        summaryRow.put("completion_tokens", 1356L);
        summaryRow.put("success_calls", 11L);
        summaryRow.put("failed_calls", 1L);
        summaryRow.put("run_count", 4L);
        summaryRow.put("conversation_count", 3L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(summaryRow), List.of(Map.of(
                    "usage_date", LocalDate.of(2026, 9, 22),
                    "model_calls", 3L,
                    "total_tokens", 900L,
                    "success_calls", 3L,
                    "failed_calls", 0L
                )));

        ApiKeyUsageStatsService service = new ApiKeyUsageStatsService(jdbcTemplate);

        var result = service.query(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 22), 7L);

        assertThat(result.summaries()).singleElement().satisfies(summary -> {
            assertThat(summary.apiKeyId()).isEqualTo(7L);
            assertThat(summary.apiKeyName()).isEqualTo("生产 Key");
            assertThat(summary.modelCalls()).isEqualTo(12L);
            assertThat(summary.totalTokens()).isEqualTo(3456L);
            assertThat(summary.successCalls()).isEqualTo(11L);
            assertThat(summary.failedCalls()).isEqualTo(1L);
        });
        assertThat(result.daily()).singleElement().satisfies(daily -> {
            assertThat(daily.usageDate()).isEqualTo(LocalDate.of(2026, 9, 22));
            assertThat(daily.modelCalls()).isEqualTo(3L);
            assertThat(daily.totalTokens()).isEqualTo(900L);
        });
    }
}
