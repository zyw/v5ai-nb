package xin.v5ai.nb.api.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.agent.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;

import java.util.List;

@RestController
@RequestMapping("/api/admin/agents")
public class AgentController {
    private final AgentManagementService agentManagementService;
    private final AgentConfigGenerator agentConfigGenerator;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public AgentController(AgentManagementService agentManagementService, AgentConfigGenerator agentConfigGenerator) {
        this.agentManagementService = agentManagementService;
        this.agentConfigGenerator = agentConfigGenerator;
    }

    /** 新建智能体向导：根据描述调用模型生成配置（名称/描述/欢迎语/预设问题/系统提示词）。 */
    @PostMapping("/generate")
    public R<GenerateAgentConfigResponse> generateAgentConfig(@RequestBody GenerateAgentConfigRequest request) {
        audit("GENERATE", "agent-config", null, "generate agent config");
        return R.ok(GenerateAgentConfigResponse.from(
                agentConfigGenerator.generate(request.modelId(), request.description())));
    }

    @PostMapping
    public R<AgentResponse> createAgent(@RequestBody CreateAgentRequest request) {
        var created = AgentResponse.from(agentManagementService.createAgent(
                new CreateAgentCommand(request.agentKey(), request.name(), request.description(),
                        request.modelId(), request.systemPrompt(),
                        request.avatar(), request.greeting(), request.presetQuestions(),
                        request.memoryEnabled(), request.mcpEnabled(), request.skillEnabled(),
                        request.webSearchEnabled(), request.ragEnabled())));
        audit("CREATE", "agent", request.agentKey(), "create agent");
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<AgentResponse>> listAgents(PageQuery pageQuery) {
        var list = agentManagementService.listAgents().stream()
                .map(AgentResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PutMapping("/{agentKey}")
    public R<AgentResponse> updateAgent(@PathVariable("agentKey") String agentKey,
                                     @RequestBody UpdateAgentRequest request) {
        audit("UPDATE", "agent", agentKey, "update agent");
        return R.ok(AgentResponse.from(agentManagementService.updateAgent(
                new UpdateAgentCommand(agentKey, request.name(), request.description(),
                        request.modelId(), request.systemPrompt(),
                        request.avatar(), request.greeting(), request.presetQuestions(),
                        request.memoryEnabled(), request.mcpEnabled(), request.skillEnabled(),
                        request.webSearchEnabled(), request.ragEnabled()))));
    }

    @PostMapping("/{agentKey}/disable")
    public R<Void> disableAgent(@PathVariable("agentKey") String agentKey) {
        agentManagementService.disable(agentKey);
        audit("DISABLE", "agent", agentKey, "disable agent");
        return R.ok();
    }

    @DeleteMapping("/{agentKey}")
    public R<Void> deleteAgent(@PathVariable("agentKey") String agentKey) {
        agentManagementService.delete(agentKey);
        audit("DELETE", "agent", agentKey, "delete agent with all related data");
        return R.ok();
    }

    @GetMapping("/{agentKey}/versions")
    public R<PageResult<AgentVersionResponse>> listVersions(@PathVariable("agentKey") String agentKey,
                                                            PageQuery pageQuery) {
        var list = agentManagementService.listVersions(agentKey).stream()
                .map(AgentVersionResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PostMapping("/{agentKey}/publish")
    public R<Void> publishAgent(@PathVariable("agentKey") String agentKey,
                                @RequestBody PublishAgentRequest request) {
        agentManagementService.publish(new PublishAgentCommand(agentKey, request.description()));
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
