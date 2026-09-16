package xin.v5ai.nb.workflow.core;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow 执行引擎：载入已发布定义，从 START 节点遍历 DAG 执行（CONDITION 分支只走匹配支路，
 * 未走支路节点记 SKIPPED），记录工作流级与节点级运行记录。AGENT 节点复用 {@link AgentRuntime}。
 */
@Service
public class WorkflowEngine {
    private final WorkflowRepository repository;
    private final WorkflowRunRepository runRepository;
    private final AgentRuntime agentRuntime;

    public WorkflowEngine(WorkflowRepository repository, WorkflowRunRepository runRepository, AgentRuntime agentRuntime) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.agentRuntime = agentRuntime;
    }

    public WorkflowRun execute(String workflowKey, Map<String, Object> inputs) {
        Workflow workflow = repository.findByKey(workflowKey);
        if (workflow == null) {
            throw new IllegalArgumentException("workflow does not exist: " + workflowKey);
        }
        if (!workflow.isPublished()) {
            throw new IllegalArgumentException("workflow is not published: " + workflowKey);
        }
        WorkflowDefinition definition = workflow.publishedDefinition();
        WorkflowDefinitionValidator.validate(definition);

        Map<String, WorkflowNode> nodeById = new HashMap<>();
        for (WorkflowNode node : definition.nodes()) {
            nodeById.put(node.id(), node);
        }
        WorkflowNode startNode = definition.nodes().stream()
                .filter(n -> n.type() == WorkflowNodeType.START)
                .findFirst()
                .orElseThrow();

        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        Map<String, Object> runInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
        runRepository.start(new WorkflowRun(runId, workflowKey, workflow.publishedVersion(),
                WorkflowRunStatus.RUNNING, runInputs, null, null, startedAt, null, startedAt));

        Map<String, Object> variables = new LinkedHashMap<>();
        seedStartVariables(startNode, variables);
        variables.putAll(runInputs);

        ExecutionContext context = new ExecutionContext(runId, nodeById, definition, variables);
        try {
            visit(startNode.id(), context);
            for (WorkflowNode node : definition.nodes()) {
                if (!context.visited.contains(node.id())) {
                    runRepository.saveNodeRun(new WorkflowNodeRun(null, runId, node.id(), node.type(),
                            WorkflowNodeRunStatus.SKIPPED, null, null, "skipped (branch not taken)",
                            Instant.now(), Instant.now()));
                }
            }
            Map<String, Object> outputs = new LinkedHashMap<>(context.finalOutputs);
            Instant finishedAt = Instant.now();
            runRepository.complete(runId, outputs, finishedAt);
            return new WorkflowRun(runId, workflowKey, workflow.publishedVersion(),
                    WorkflowRunStatus.SUCCEEDED, runInputs, outputs, null, startedAt, finishedAt, startedAt);
        } catch (RuntimeException exception) {
            Instant finishedAt = Instant.now();
            runRepository.fail(runId, exception.getMessage(), finishedAt);
            return new WorkflowRun(runId, workflowKey, workflow.publishedVersion(),
                    WorkflowRunStatus.FAILED, runInputs, null, exception.getMessage(), startedAt, finishedAt, startedAt);
        }
    }

    private void seedStartVariables(WorkflowNode startNode, Map<String, Object> variables) {
        Object vars = startNode.config() == null ? null : startNode.config().get("variables");
        if (vars instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    if (name != null) {
                        variables.putIfAbsent(String.valueOf(name), map.get("default"));
                    }
                }
            }
        }
    }

    private void visit(String nodeId, ExecutionContext context) {
        if (context.visited.contains(nodeId)) {
            return;
        }
        context.visited.add(nodeId);
        WorkflowNode node = context.nodeById.get(nodeId);

        WorkflowNodeRun nodeRun;
        try {
            nodeRun = executeNode(node, context);
        } catch (RuntimeException exception) {
            runRepository.saveNodeRun(new WorkflowNodeRun(null, context.runId, node.id(), node.type(),
                    WorkflowNodeRunStatus.FAILED, snapshot(context.variables), null, exception.getMessage(),
                    Instant.now(), Instant.now()));
            throw exception;
        }
        runRepository.saveNodeRun(nodeRun);

        List<WorkflowEdge> outgoing = context.outEdges(node.id());
        if (node.type() == WorkflowNodeType.CONDITION) {
            boolean branch = Boolean.TRUE.equals(nodeRun.outputs().get("result"));
            for (WorkflowEdge edge : outgoing) {
                if (String.valueOf(branch).equals(edge.sourceHandle())) {
                    visit(edge.target(), context);
                }
            }
        } else {
            for (WorkflowEdge edge : outgoing) {
                visit(edge.target(), context);
            }
        }
    }

    private WorkflowNodeRun executeNode(WorkflowNode node, ExecutionContext context) {
        Instant started = Instant.now();
        Map<String, Object> inputs = snapshot(context.variables);
        Map<String, Object> outputs = new LinkedHashMap<>();
        switch (node.type()) {
            case START -> {
                // 输入变量已在 seedStartVariables 中初始化，START 本身无输出。
            }
            case AGENT -> executeAgent(node, context, outputs);
            case CONDITION -> executeCondition(node, context, outputs);
            case END -> {
                Map<String, Object> endOutputs = collectEndOutputs(node, context);
                outputs.putAll(endOutputs);
                context.finalOutputs.putAll(endOutputs);
            }
        }
        return new WorkflowNodeRun(null, context.runId, node.id(), node.type(),
                WorkflowNodeRunStatus.SUCCEEDED, inputs, outputs, null, started, Instant.now());
    }

    private void executeAgent(WorkflowNode node, ExecutionContext context, Map<String, Object> outputs) {
        Map<String, Object> config = node.config() == null ? Map.of() : node.config();
        String agentKey = str(config.get("agentKey"));
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agent node requires agentKey: " + node.id());
        }
        String prompt = TemplateResolver.resolve(str(config.get("prompt")), context.variables);
        String outputVar = str(config.get("outputVar"));
        if (outputVar == null || outputVar.isBlank()) {
            outputVar = node.id();
        }
        var result = agentRuntime.call(new AgentRunBo(agentKey, context.runId, prompt)).block();
        String answer = result == null ? "" : result.answer();
        context.variables.put(outputVar, answer);
        outputs.put("answer", answer);
        outputs.put("outputVar", outputVar);
    }

    private void executeCondition(WorkflowNode node, ExecutionContext context, Map<String, Object> outputs) {
        Map<String, Object> config = node.config() == null ? Map.of() : node.config();
        String operator = str(config.get("operator"));
        Object left = TemplateResolver.resolveRefOrLiteral(str(config.get("left")), context.variables);
        Object right = TemplateResolver.resolveRefOrLiteral(str(config.get("right")), context.variables);
        boolean result = ConditionEvaluator.evaluate(left, operator, right);
        outputs.put("result", result);
        outputs.put("left", left);
        outputs.put("right", right);
    }

    private Map<String, Object> collectEndOutputs(WorkflowNode node, ExecutionContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object outputs = node.config() == null ? null : node.config().get("outputs");
        if (outputs instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    if (name != null) {
                        result.put(String.valueOf(name),
                                TemplateResolver.resolveRefOrLiteral(str(map.get("value")), context.variables));
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Object> snapshot(Map<String, Object> variables) {
        return new LinkedHashMap<>(variables);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class ExecutionContext {
        final String runId;
        final Map<String, WorkflowNode> nodeById;
        final WorkflowDefinition definition;
        final Map<String, Object> variables;
        final Map<String, Object> finalOutputs = new LinkedHashMap<>();
        final Set<String> visited = new HashSet<>();

        ExecutionContext(String runId, Map<String, WorkflowNode> nodeById,
                         WorkflowDefinition definition, Map<String, Object> variables) {
            this.runId = runId;
            this.nodeById = nodeById;
            this.definition = definition;
            this.variables = variables;
        }

        List<WorkflowEdge> outEdges(String source) {
            List<WorkflowEdge> result = new ArrayList<>();
            for (WorkflowEdge edge : definition.edges()) {
                if (source.equals(edge.source())) {
                    result.add(edge);
                }
            }
            return result;
        }
    }
}
