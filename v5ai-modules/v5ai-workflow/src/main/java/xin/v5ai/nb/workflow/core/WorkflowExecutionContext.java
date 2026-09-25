package xin.v5ai.nb.workflow.core;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/** State shared by nodes during one workflow run. */
public final class WorkflowExecutionContext {
    private final String runId;
    private final String workflowKey;
    private final Long workflowVersion;
    private final Map<String, Object> inputs;
    private final Map<String, Object> variables;
    private final Map<String, Map<String, Object>> nodeOutputs = new LinkedHashMap<>();
    private final Map<String, Object> finalOutputs = new LinkedHashMap<>();
    private final Long apiKeyId;
    private final Long userId;

    public WorkflowExecutionContext(String runId, String workflowKey, Long workflowVersion,
                                    Map<String, Object> inputs, Map<String, Object> variables) {
        this(runId, workflowKey, workflowVersion, inputs, variables, null, null);
    }

    public WorkflowExecutionContext(String runId, String workflowKey, Long workflowVersion,
                                    Map<String, Object> inputs, Map<String, Object> variables,
                                    Long apiKeyId, Long userId) {
        this.runId = runId;
        this.workflowKey = workflowKey;
        this.workflowVersion = workflowVersion;
        this.inputs = new LinkedHashMap<>(inputs);
        this.variables = new LinkedHashMap<>(variables);
        this.apiKeyId = apiKeyId;
        this.userId = userId;
    }

    public String runId() { return runId; }
    public String workflowKey() { return workflowKey; }
    public Long workflowVersion() { return workflowVersion; }
    public Long apiKeyId() { return apiKeyId; }
    public Long userId() { return userId; }
    public Map<String, Object> inputs() { return Collections.unmodifiableMap(new LinkedHashMap<>(inputs)); }
    public Map<String, Object> variables() { return variables; }
    public Map<String, Map<String, Object>> nodeOutputs() { return nodeOutputs; }
    public Map<String, Object> finalOutputs() { return finalOutputs; }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inputs", new LinkedHashMap<>(inputs));
        snapshot.putAll(variables);
        snapshot.put("nodes", new LinkedHashMap<String, Map<String, Object>>(nodeOutputs));
        return snapshot;
    }

    public Object resolve(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("inputs.")) return TemplateResolver.lookupPath(path.substring(7), inputs);
        if (path.startsWith("nodes.")) return TemplateResolver.lookupPath(path.substring(6), nodeOutputs);
        if (path.startsWith("vars.")) return TemplateResolver.lookupPath(path.substring(5), variables);
        if (path.startsWith("system.")) {
            return switch (path.substring(7)) {
                case "runId" -> runId;
                case "workflowKey" -> workflowKey;
                case "workflowVersion" -> workflowVersion;
                default -> null;
            };
        }
        // v1 compatibility: old workflows addressed flat variables directly.
        if (variables.containsKey(path)) return variables.get(path);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("inputs", inputs);
        root.putAll(variables);
        root.put("nodes", nodeOutputs);
        return TemplateResolver.lookupPath(path, root);
    }

    public void recordNodeOutput(String nodeId, Map<String, Object> output) {
        nodeOutputs.put(nodeId, new LinkedHashMap<>(output));
    }

    public void setVariable(String name, Object value) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid workflow variable name: " + name);
        }
        variables.put(name, value);
    }
}
