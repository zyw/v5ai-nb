package xin.v5ai.nb.api.agent;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.agent.AgentConfigGenerator;
import xin.v5ai.nb.agent.AgentManagementService;
import xin.v5ai.nb.agent.CreateAgentCommand;
import xin.v5ai.nb.agent.PublishAgentCommand;
import xin.v5ai.nb.agent.UpdateAgentCommand;
import xin.v5ai.nb.common.core.platform.AuditRecorder;

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
    public GenerateAgentConfigResponse generateAgentConfig(@RequestBody GenerateAgentConfigRequest request) {
        audit("GENERATE", "agent-config", null, "generate agent config");
        return GenerateAgentConfigResponse.from(
                agentConfigGenerator.generate(request.modelId(), request.description()));
    }

    @PostMapping
    public AgentResponse createAgent(@RequestBody CreateAgentRequest request) {
        var created = AgentResponse.from(agentManagementService.createAgent(
                new CreateAgentCommand(request.agentKey(), request.name(), request.description(),
                        request.modelId(), request.systemPrompt(),
                        request.avatar(), request.greeting(), request.presetQuestions(),
                        request.memoryEnabled(), request.mcpEnabled(), request.skillEnabled(),
                        request.webSearchEnabled(), request.ragEnabled())));
        audit("CREATE", "agent", request.agentKey(), "create agent");
        return created;
    }

    @GetMapping
    public List<AgentResponse> listAgents() {
        return agentManagementService.listAgents().stream()
                .map(AgentResponse::from)
                .toList();
    }

    @PutMapping("/{agentKey}")
    public AgentResponse updateAgent(@PathVariable("agentKey") String agentKey,
                                     @RequestBody UpdateAgentRequest request) {
        audit("UPDATE", "agent", agentKey, "update agent");
        return AgentResponse.from(agentManagementService.updateAgent(
                new UpdateAgentCommand(agentKey, request.name(), request.description(),
                        request.modelId(), request.systemPrompt(),
                        request.avatar(), request.greeting(), request.presetQuestions(),
                        request.memoryEnabled(), request.mcpEnabled(), request.skillEnabled(),
                        request.webSearchEnabled(), request.ragEnabled())));
    }

    @PostMapping("/{agentKey}/disable")
    public void disableAgent(@PathVariable("agentKey") String agentKey) {
        agentManagementService.disable(agentKey);
        audit("DISABLE", "agent", agentKey, "disable agent");
    }

    @DeleteMapping("/{agentKey}")
    public void deleteAgent(@PathVariable("agentKey") String agentKey) {
        agentManagementService.delete(agentKey);
        audit("DELETE", "agent", agentKey, "delete agent with all related data");
    }

    @GetMapping("/{agentKey}/versions")
    public List<AgentVersionResponse> listVersions(@PathVariable("agentKey") String agentKey) {
        return agentManagementService.listVersions(agentKey).stream()
                .map(AgentVersionResponse::from)
                .toList();
    }

    @PostMapping("/{agentKey}/publish")
    public void publishAgent(@PathVariable("agentKey") String agentKey,
                             @RequestBody PublishAgentRequest request) {
        agentManagementService.publish(new PublishAgentCommand(agentKey, request.description()));
        audit("PUBLISH", "agent", agentKey, "publish agent");
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
