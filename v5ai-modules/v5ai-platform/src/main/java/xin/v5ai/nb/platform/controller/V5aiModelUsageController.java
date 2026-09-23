package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.ModelUsagePageVo;
import xin.v5ai.nb.platform.service.IV5aiModelUsageService;

/**
 * 用量明细查询 API（可观测性）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/usage")
public class V5aiModelUsageController {

    private final IV5aiModelUsageService service;

    @SaCheckPermission("monitor:usage:list")
    @GetMapping
    public R<PageResult<ModelUsagePageVo>> list(ModelUsageListBo bo, PageQuery pageQuery) {
//        var fromInstant = parseDayStart(from);
//        var toInstant = parseDayEnd(to);
//        int limit = pageQuery.getPageSize() == null ? 100 : pageQuery.getPageSize();
//        List<V5aiModelUsage> entities = (agentKey == null || agentKey.isBlank())
//                ? usageMapper.selectRecent(limit)
//                : usageMapper.selectByAppRange(agentKey, fromInstant, toInstant, limit);
//        var records = entities.stream().map(entity -> new UsageRecord(
//                entity.getId(), entity.getRunId(), entity.getAgentKey(), entity.getModelKey(),
//                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getDurationMs(),
//                entity.getStatus(), entity.getCreatedAt())).toList();
        return R.ok(service.queryPageList(bo, pageQuery));
    }

//    private static Instant parseDayStart(String date) {
//        if (date == null || date.isBlank()) {
//            return Instant.EPOCH;
//        }
//        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
//    }
//
//    private static Instant parseDayEnd(String date) {
//        if (date == null || date.isBlank()) {
//            return Instant.now().plusSeconds(86400);
//        }
//        return LocalDate.parse(date).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
//    }
}
