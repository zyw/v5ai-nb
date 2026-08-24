package xin.v5ai.nb.agent.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.agent.controller.vo.GenerateAgentConfigRequest;
import xin.v5ai.nb.agent.controller.vo.GenerateAgentConfigResponse;
import xin.v5ai.nb.agent.controller.vo.PublishAgentRequest;
import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVersionVo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.agent.service.IAgentService;
import xin.v5ai.nb.common.agentscope.core.AgentConfigGenerator;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.platform.AuditLogRecorderService;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.web.core.BaseController;

import java.util.List;

/**
 * Agent 管理 API：新建（含向导生成配置）、分页列表、更新、禁用、删除、版本列表与发布。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agents")
public class AgentController extends BaseController {

    private final IAgentService agentService;
    private final AgentConfigGenerator agentConfigGenerator;

    @Autowired(required = false)
    private AuditLogRecorderService auditRecorder;

    /** 新建智能体向导：根据描述调用模型生成配置（名称/描述/欢迎语/预设问题/系统提示词）。 */
    @PostMapping("/generate")
    public R<GenerateAgentConfigResponse> generateAgentConfig(@RequestBody GenerateAgentConfigRequest request) {
        audit("GENERATE", "agent-config", null, "generate agent config");
        return R.ok(GenerateAgentConfigResponse.from(
                agentConfigGenerator.generate(request.modelId(), request.description())));
    }

    @PostMapping
    public R<AgentVo> createAgent(@Validated(AddGroup.class) @RequestBody AgentBo bo) {
        var created = agentService.createAgent(bo);
        audit("CREATE", "agent", created.getAgentKey(), "create agent");
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<AgentVo>> listAgents(AgentBo bo, PageQuery pageQuery) {
        return R.ok(agentService.queryPageList(bo, pageQuery));
    }

    @GetMapping("/{agentKey}")
    public R<AgentVo> getAgent(@PathVariable("agentKey") String agentKey) {
        return R.ok(agentService.getAgent(agentKey));
    }

    @PutMapping("/{agentKey}")
    public R<AgentVo> updateAgent(@PathVariable("agentKey") String agentKey,
                                  @RequestBody AgentBo bo) {
        var updated = agentService.updateAgent(agentKey, bo);
        audit("UPDATE", "agent", agentKey, "update agent");
        return R.ok(updated);
    }

    @PostMapping("/{agentKey}/disable")
    public R<Void> disableAgent(@PathVariable("agentKey") String agentKey) {
        agentService.disable(agentKey);
        audit("DISABLE", "agent", agentKey, "disable agent");
        return R.ok();
    }

    @DeleteMapping("/{agentKey}")
    public R<Void> deleteAgent(@PathVariable("agentKey") String agentKey) {
        agentService.delete(agentKey);
        audit("DELETE", "agent", agentKey, "delete agent with all related data");
        return R.ok();
    }

    @GetMapping("/{agentKey}/versions")
    public R<PageResult<AgentVersionVo>> listVersions(@PathVariable("agentKey") String agentKey,
                                                      PageQuery pageQuery) {
        List<AgentVersionVo> list = agentService.listVersions(agentKey);
        return R.ok(PageResult.build(list, (long) list.size()));
    }

    @PostMapping("/{agentKey}/publish")
    public R<Void> publishAgent(@PathVariable("agentKey") String agentKey,
                                @RequestBody PublishAgentRequest request) {
        agentService.publish(agentKey, request == null ? null : request.description());
        audit("PUBLISH", "agent", agentKey, "publish agent");
        return R.ok();
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = LoginHelper.getUsername();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
