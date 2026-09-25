package xin.v5ai.nb.workflow.core.executor;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.TemplateResolver;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentNodeExecutor implements WorkflowNodeExecutor {
    private final AgentRuntime agentRuntime;

    public AgentNodeExecutor(AgentRuntime agentRuntime) { this.agentRuntime = agentRuntime; }

    @Override public WorkflowNodeType type() { return WorkflowNodeType.AGENT; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        String agentKey = text(node.config().get("agentKey"));
        if (agentKey == null || agentKey.isBlank()) throw new IllegalArgumentException("agent node requires agentKey: " + node.id());
        String prompt = TemplateResolver.resolve(text(node.config().get("prompt")), context);
        long timeoutMs = positiveLong(node.config().get("timeoutMs"), 120_000L);
        var response = agentRuntime.call(new AgentRunBo(agentKey, context.runId(), prompt)
                        .withRunId(context.runId()).withCaller(context.apiKeyId(), context.userId()))
                .block(Duration.ofMillis(timeoutMs));
        String answer = response == null ? "" : response.answer();
        String outputVar = text(node.config().get("outputVar"));
        if (outputVar == null || outputVar.isBlank()) outputVar = node.id();
        context.setVariable(outputVar, answer);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", answer);
        output.put("agentRunId", response == null ? null : response.runId());
        output.put("outputVar", outputVar);
        return NodeExecutionResult.of(output);
    }

    public static String text(Object value) { return value == null ? null : String.valueOf(value); }
    static long positiveLong(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            if (parsed < 1) throw new IllegalArgumentException("timeoutMs must be positive");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("timeoutMs must be a positive integer", exception);
        }
    }
}
