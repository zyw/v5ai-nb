package xin.v5ai.nb.api.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.platform.UsageRecord;
import xin.v5ai.nb.infrastructure.platform.ModelUsageEntity;
import xin.v5ai.nb.infrastructure.platform.ModelUsageMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 用量明细查询 API（可观测性）。
 */
@RestController
@RequestMapping("/api/admin/usage")
public class UsageController {
    private final ModelUsageMapper usageMapper;

    public UsageController(ModelUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @GetMapping
    public List<UsageRecord> listUsage(
            @RequestParam(value = "agentKey", required = false) String agentKey,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        var fromInstant = parseDayStart(from);
        var toInstant = parseDayEnd(to);
        List<ModelUsageEntity> entities = (agentKey == null || agentKey.isBlank())
                ? usageMapper.selectRecent(limit)
                : usageMapper.selectByAppRange(agentKey, fromInstant, toInstant, limit);
        return entities.stream().map(entity -> new UsageRecord(
                entity.getId(), entity.getRunId(), entity.getAgentKey(), entity.getModelKey(),
                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getDurationMs(),
                entity.getStatus(), entity.getCreatedAt())).toList();
    }

    private static Instant parseDayStart(String date) {
        if (date == null || date.isBlank()) {
            return Instant.EPOCH;
        }
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant parseDayEnd(String date) {
        if (date == null || date.isBlank()) {
            return Instant.now().plusSeconds(86400);
        }
        return LocalDate.parse(date).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
