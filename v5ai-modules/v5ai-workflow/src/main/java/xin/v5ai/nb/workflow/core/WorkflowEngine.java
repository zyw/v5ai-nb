package xin.v5ai.nb.workflow.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunSource;
import xin.v5ai.nb.workflow.core.executor.*;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executes one immutable published workflow snapshot through registered node executors. */
@Service
public class WorkflowEngine {
    private static final int MAX_NODES_PER_RUN = 500;
    private static final int MAX_STATE_BYTES = 1_048_576;
    private final WorkflowRepository repository;
    private final WorkflowRunRepository runRepository;
    private final WorkflowNodeExecutorRegistry executors;

    @Autowired
    public WorkflowEngine(WorkflowRepository repository, WorkflowRunRepository runRepository,
                          List<WorkflowNodeExecutor> nodeExecutors) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.executors = new WorkflowNodeExecutorRegistry(nodeExecutors);
    }

    /** Compatibility constructor for direct engine construction in existing module consumers. */
    public WorkflowEngine(WorkflowRepository repository, WorkflowRunRepository runRepository, AgentRuntime agentRuntime) {
        this(repository, runRepository, List.of(new StartNodeExecutor(), new AgentNodeExecutor(agentRuntime),
                new ConditionNodeExecutor(), new VariableNodeExecutor(), new EndNodeExecutor(),
                new HttpNodeExecutor(""), new PythonNodeExecutor("", "")));
    }

    public WorkflowRun execute(String workflowKey, Map<String, Object> inputs) {
        Workflow workflow = repository.findByKey(workflowKey);
        if (workflow == null) throw new IllegalArgumentException("workflow does not exist: " + workflowKey);
        if (!workflow.isPublished()) throw new IllegalArgumentException("workflow is not published: " + workflowKey);
        return executeDefinition(workflowKey, workflow.publishedVersion(), workflow.publishedDefinition(), inputs);
    }

    public WorkflowRun executePublic(String workflowKey, Map<String, Object> inputs, Long apiKeyId, Long userId) {
        Workflow workflow = repository.findByKey(workflowKey);
        if (workflow == null) throw new IllegalArgumentException("workflow does not exist: " + workflowKey);
        if (!workflow.isPublished()) throw new IllegalArgumentException("workflow is not published: " + workflowKey);
        return executeDefinition(workflowKey, workflow.publishedVersion(), workflow.publishedDefinition(), inputs,
                WorkflowRunSource.PUBLISHED, null, apiKeyId, userId);
    }

    public WorkflowRun executeDefinition(String workflowKey, Long version, WorkflowDefinition definition,
                                         Map<String, Object> inputs) {
        return executeDefinition(workflowKey, version, definition, inputs, WorkflowRunSource.PUBLISHED, null, null, null);
    }

    public WorkflowRun executeDraft(String workflowKey, Long revision, WorkflowDefinition definition,
                                    Map<String, Object> inputs) {
        return executeDefinition(workflowKey, null, definition, inputs, WorkflowRunSource.DRAFT_TEST, revision, null, null);
    }

    private WorkflowRun executeDefinition(String workflowKey, Long version, WorkflowDefinition definition,
                                          Map<String, Object> inputs, WorkflowRunSource source, Long draftRevision,
                                          Long apiKeyId, Long userId) {
        WorkflowDefinitionValidator.validate(definition);
        for (WorkflowNode node : definition.nodes()) {
            if (!executors.supports(node.type())) throw new IllegalArgumentException("unsupported workflow node type: " + node.type());
        }
        Map<String, WorkflowNode> byId = new HashMap<>();
        Map<String, List<WorkflowEdge>> outgoing = new HashMap<>();
        for (WorkflowNode node : definition.nodes()) byId.put(node.id(), node);
        for (WorkflowEdge edge : definition.edges()) outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
        WorkflowNode start = definition.nodes().stream().filter(n -> n.type() == WorkflowNodeType.START).findFirst().orElseThrow();

        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        Map<String, Object> runInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
        enforceStateLimit(runInputs, "workflow inputs");
        runRepository.start(new WorkflowRun(runId, workflowKey, version, WorkflowRunStatus.RUNNING,
                runInputs, null, null, startedAt, null, startedAt, source, draftRevision,
                source == WorkflowRunSource.DRAFT_TEST ? WorkflowJson.toJson(definition) : null));
        Map<String, Object> variables = new LinkedHashMap<>();
        seedDefaults(start, variables);
        variables.putAll(runInputs);
        WorkflowExecutionContext context = new WorkflowExecutionContext(runId, workflowKey, version, runInputs,
                variables, apiKeyId, userId);
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(start.id());

        try {
            while (!pending.isEmpty()) {
                if (visited.size() >= MAX_NODES_PER_RUN) throw new IllegalStateException("workflow exceeded maximum node count");
                String nodeId = pending.removeFirst();
                if (!visited.add(nodeId)) continue;
                WorkflowNode node = byId.get(nodeId);
                Instant nodeStarted = Instant.now();
                Map<String, Object> nodeInputs = context.snapshot();
                runRepository.startNodeRun(new WorkflowNodeRun(null, runId, node.id(), node.type(),
                        WorkflowNodeRunStatus.RUNNING, nodeInputs, null, null, nodeStarted, null));
                NodeExecutionResult result;
                try {
                    result = executors.require(node.type()).execute(node, context);
                    enforceStateLimit(result.outputs(), "node output " + node.id());
                } catch (RuntimeException error) {
                    runRepository.finishNodeRun(new WorkflowNodeRun(null, runId, node.id(), node.type(),
                            WorkflowNodeRunStatus.FAILED, nodeInputs, null, safeMessage(error), nodeStarted, Instant.now()));
                    throw error;
                }
                context.recordNodeOutput(node.id(), result.outputs());
                runRepository.finishNodeRun(new WorkflowNodeRun(null, runId, node.id(), node.type(),
                        WorkflowNodeRunStatus.SUCCEEDED, nodeInputs, result.outputs(), null, nodeStarted, Instant.now()));

                List<WorkflowEdge> edges = outgoing.getOrDefault(node.id(), List.of());
                if (node.type() == WorkflowNodeType.CONDITION) {
                    String handle = result.nextHandle();
                    edges.stream().filter(edge -> handle.equals(edge.sourceHandle())).findFirst()
                            .ifPresent(edge -> pending.addLast(edge.target()));
                } else {
                    edges.forEach(edge -> pending.addLast(edge.target()));
                }
            }
            for (WorkflowNode node : definition.nodes()) {
                if (!visited.contains(node.id())) {
                    Instant now = Instant.now();
                    runRepository.saveNodeRun(new WorkflowNodeRun(null, runId, node.id(), node.type(),
                            WorkflowNodeRunStatus.SKIPPED, null, null, "skipped (branch not taken)", now, now));
                }
            }
            Instant finishedAt = Instant.now();
            Map<String, Object> outputs = definition.outputs().isEmpty()
                    ? new LinkedHashMap<>(context.finalOutputs()) : resolveWorkflowOutputs(definition.outputs(), context);
            enforceStateLimit(outputs, "workflow outputs");
            runRepository.complete(runId, outputs, finishedAt);
            return new WorkflowRun(runId, workflowKey, version, WorkflowRunStatus.SUCCEEDED,
                    runInputs, outputs, null, startedAt, finishedAt, startedAt, source, draftRevision,
                    source == WorkflowRunSource.DRAFT_TEST ? WorkflowJson.toJson(definition) : null);
        } catch (RuntimeException error) {
            for (WorkflowNode node : definition.nodes()) {
                if (!visited.contains(node.id())) {
                    Instant now = Instant.now();
                    runRepository.saveNodeRun(new WorkflowNodeRun(null, runId, node.id(), node.type(),
                            WorkflowNodeRunStatus.SKIPPED, null, null, "skipped (workflow failed before execution)", now, now));
                }
            }
            Instant finishedAt = Instant.now();
            runRepository.fail(runId, safeMessage(error), finishedAt);
            return new WorkflowRun(runId, workflowKey, version, WorkflowRunStatus.FAILED,
                    runInputs, null, safeMessage(error), startedAt, finishedAt, startedAt, source, draftRevision,
                    source == WorkflowRunSource.DRAFT_TEST ? WorkflowJson.toJson(definition) : null);
        }
    }

    private void enforceStateLimit(Object value, String label) {
        try {
            if (WorkflowJson.MAPPER.writeValueAsBytes(value).length > MAX_STATE_BYTES) {
                throw new IllegalArgumentException(label + " exceed 1 MiB");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(label + " cannot be serialized", exception);
        }
    }

    private void seedDefaults(WorkflowNode start, Map<String, Object> variables) {
        Object configured = start.config().get("variables");
        if (configured instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("name") != null) {
                    variables.put(String.valueOf(map.get("name")), map.get("default"));
                }
            }
        }
    }

    private Map<String, Object> resolveWorkflowOutputs(List<Map<String, Object>> definitions,
                                                       WorkflowExecutionContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> definition : definitions) {
            Object name = definition.get("name");
            if (name != null) result.put(String.valueOf(name), TemplateResolver.resolveRefOrLiteral(
                    AgentNodeExecutor.text(definition.get("value")), context));
        }
        return result;
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
