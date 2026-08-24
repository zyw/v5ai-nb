package xin.v5ai.nb.platform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.UsageRecord;
import xin.v5ai.nb.platform.domain.ModelUsage;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.ModelUsageRecordVo;
import xin.v5ai.nb.platform.domain.vo.ModelUsageVo;
import xin.v5ai.nb.platform.mapper.ModelUsageMapper;
import xin.v5ai.nb.platform.service.IModelUsageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 用量明细查询 API（可观测性）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/usage")
public class UsageController {

    private final IModelUsageService service;

    @GetMapping
    public R<PageResult<ModelUsageRecordVo>> listUsage(ModelUsageListBo bo, PageQuery pageQuery) {
//        var fromInstant = parseDayStart(from);
//        var toInstant = parseDayEnd(to);
//        int limit = pageQuery.getPageSize() == null ? 100 : pageQuery.getPageSize();
//        List<ModelUsage> entities = (agentKey == null || agentKey.isBlank())
//                ? usageMapper.selectRecent(limit)
//                : usageMapper.selectByAppRange(agentKey, fromInstant, toInstant, limit);
//        var records = entities.stream().map(entity -> new UsageRecord(
//                entity.getId(), entity.getRunId(), entity.getAgentKey(), entity.getModelKey(),
//                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getDurationMs(),
//                entity.getStatus(), entity.getCreatedAt())).toList();
        return R.ok(service.queryPageList(bo, pageQuery));
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
