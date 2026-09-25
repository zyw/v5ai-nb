package xin.v5ai.nb.workflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;
import xin.v5ai.nb.workflow.core.WorkflowEngine;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowRun;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.domain.bo.RunWorkflowBo;
import xin.v5ai.nb.workflow.service.IWorkflowService;

import java.util.Set;
import java.util.stream.Collectors;

/** Public published-workflow execution endpoint guarded by the shared API-key filter. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workflows")
public class WorkflowRuntimeController {
    private final IWorkflowService workflowService;
    private final WorkflowEngine engine;
    private final AppQuotaService quotaService;

    @PostMapping("/{workflowKey}/run")
    public R<WorkflowRun> run(@PathVariable String workflowKey,
                              @RequestBody(required = false) RunWorkflowBo request,
                              HttpServletRequest servletRequest) {
        Object value = servletRequest.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE);
        if (!(value instanceof ApiKeysAuthDTO auth)) throw new IllegalStateException("API Key authentication is required");
        var workflow = workflowService.get(workflowKey);
        if (!"PUBLISHED".equals(workflow.getStatus()) || workflow.getPublishedDefinition() == null) {
            throw new IllegalArgumentException("workflow is not published: " + workflowKey);
        }
        Set<String> agentKeys = workflow.getPublishedDefinition().nodes().stream()
                .filter(node -> node.type() == WorkflowNodeType.AGENT)
                .map(node -> String.valueOf(node.config().get("agentKey")))
                .filter(key -> !key.isBlank() && !"null".equals(key))
                .collect(Collectors.toSet());
        if (agentKeys.isEmpty()) throw new IllegalArgumentException("public workflow must reference at least one Agent");
        for (String agentKey : agentKeys) {
            if (!auth.allows(agentKey)) throw new SecurityException("API Key is not bound to workflow Agent: " + agentKey);
            if (!quotaService.checkAllowed(agentKey)) throw new IllegalStateException("Agent quota or rate limit exceeded: " + agentKey);
        }
        return R.ok(engine.executePublic(workflowKey, request == null ? java.util.Map.of() : request.inputs(),
                auth.apiKeyId(), auth.userId()));
    }
}
