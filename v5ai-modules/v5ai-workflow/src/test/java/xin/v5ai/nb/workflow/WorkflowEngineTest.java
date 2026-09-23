package xin.v5ai.nb.workflow;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.workflow.core.*;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineTest {

    @Test
    void linearFlowRunsAllNodesAndCollectsOutputs() {
        var fake = new FakeAgentRuntime();
        var engine = engine(fake, linearDefinition());

        var run = engine.execute("linear", Map.of());

        assertThat(run.status()).isEqualTo(WorkflowRunStatus.SUCCEEDED);
        assertThat(run.outputs()).containsEntry("summary", "answer:summarize weather");

        // 模板解析：AGENT 节点的 prompt 被替换为变量值
        assertThat(fake.requests).hasSize(1);
        assertThat(fake.requests.get(0).query()).isEqualTo("summarize weather");
    }

    @Test
    void inputsOverrideStartVariableDefaults() {
        var fake = new FakeAgentRuntime();
        var engine = engine(fake, linearDefinition());

        engine.execute("linear", Map.of("topic", "量子计算"));

        assertThat(fake.requests.get(0).query()).isEqualTo("summarize 量子计算");
    }

    @Test
    void linearFlowPersistsSucceededNodeRuns() {
        var runRepo = new InMemoryRunRepository();
        var engine = engine(runRepo, linearDefinition());
        var run = engine.execute("linear", Map.of());

        var nodeRuns = runRepo.findNodeRuns(run.runId());
        assertThat(nodeRuns).hasSize(3);
        assertThat(nodeRuns).extracting(WorkflowNodeRun::status)
                .containsOnly(WorkflowNodeRunStatus.SUCCEEDED);
        assertThat(nodeRuns).extracting(WorkflowNodeRun::nodeId)
                .containsExactlyInAnyOrder("start", "agent", "end");
    }

    @Test
    void conditionTrueBranchSkipsFalseBranch() {
        var runRepo = new InMemoryRunRepository();
        var engine = engine(runRepo, new FakeAgentRuntime(), conditionDefinition(), "cond");

        var run = engine.execute("cond", Map.of("score", 20));

        assertThat(run.status()).isEqualTo(WorkflowRunStatus.SUCCEEDED);
        var statuses = statusById(runRepo, run.runId());
        assertThat(statuses.get("high")).isEqualTo(WorkflowNodeRunStatus.SUCCEEDED);
        assertThat(statuses.get("low")).isEqualTo(WorkflowNodeRunStatus.SKIPPED);
    }

    @Test
    void conditionFalseBranchSkipsTrueBranch() {
        var runRepo = new InMemoryRunRepository();
        var engine = engine(runRepo, new FakeAgentRuntime(), conditionDefinition(), "cond");

        var run = engine.execute("cond", Map.of("score", 5));

        assertThat(run.status()).isEqualTo(WorkflowRunStatus.SUCCEEDED);
        var statuses = statusById(runRepo, run.runId());
        assertThat(statuses.get("low")).isEqualTo(WorkflowNodeRunStatus.SUCCEEDED);
        assertThat(statuses.get("high")).isEqualTo(WorkflowNodeRunStatus.SKIPPED);
    }

    @Test
    void rejectsUnpublishedWorkflow() {
        var repo = new InMemoryWorkflowRepository();
        repo.save(new Workflow(null, "draft", "Draft", null, WorkflowStatus.DRAFT,
                linearDefinition(), null, null, null, OffsetDateTime.now(), OffsetDateTime.now()));
        var engine = new WorkflowEngine(repo, new InMemoryRunRepository(), new FakeAgentRuntime());

        assertThatThrownBy(() -> engine.execute("draft", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not published");
    }

    @Test
    void agentNodeRequiresAgentKey() {
        var definition = new WorkflowDefinition(
                List.of(
                        node("start", WorkflowNodeType.START, "开始", Map.of()),
                        node("agent", WorkflowNodeType.AGENT, "坏节点", Map.of()),
                        node("end", WorkflowNodeType.END, "结束", Map.of())),
                List.of(
                        edge("e1", "start", "agent", "out"),
                        edge("e2", "agent", "end", "out")));
        var runRepo = new InMemoryRunRepository();
        var engine = engine(runRepo, definition);

        var run = engine.execute("linear", Map.of());

        assertThat(run.status()).isEqualTo(WorkflowRunStatus.FAILED);
        var nodeRuns = runRepo.findNodeRuns(run.runId());
        assertThat(nodeRuns).anyMatch(n -> n.nodeId().equals("agent")
                && n.status() == WorkflowNodeRunStatus.FAILED);
    }

    // ---- helpers ----

    private static WorkflowEngine engine(WorkflowDefinition definition) {
        return engine(new InMemoryRunRepository(), definition);
    }

    private static WorkflowEngine engine(FakeAgentRuntime runtime, WorkflowDefinition definition) {
        return engine(new InMemoryRunRepository(), runtime, definition);
    }

    private static WorkflowEngine engine(InMemoryRunRepository runRepo, WorkflowDefinition definition) {
        return engine(runRepo, new FakeAgentRuntime(), definition, "linear");
    }

    private static WorkflowEngine engine(InMemoryRunRepository runRepo, FakeAgentRuntime runtime,
                                         WorkflowDefinition definition) {
        return engine(runRepo, runtime, definition, "linear");
    }

    private static WorkflowEngine engine(InMemoryRunRepository runRepo, FakeAgentRuntime runtime,
                                         WorkflowDefinition definition, String key) {
        var repo = new InMemoryWorkflowRepository();
        repo.save(new Workflow(null, key, "Linear", null, WorkflowStatus.PUBLISHED,
                definition, definition, 1L, OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now()));
        return new WorkflowEngine(repo, runRepo, runtime);
    }

    private static Map<String, WorkflowNodeRunStatus> statusById(InMemoryRunRepository runRepo, String runId) {
        Map<String, WorkflowNodeRunStatus> result = new LinkedHashMap<>();
        for (WorkflowNodeRun nodeRun : runRepo.findNodeRuns(runId)) {
            result.put(nodeRun.nodeId(), nodeRun.status());
        }
        return result;
    }

    private static WorkflowDefinition linearDefinition() {
        return new WorkflowDefinition(
                List.of(
                        node("start", WorkflowNodeType.START, "开始",
                                map("variables", List.of(var("topic", "weather")))),
                        node("agent", WorkflowNodeType.AGENT, "总结",
                                map("agentKey", "summarizer", "prompt", "summarize {{topic}}")),
                        node("end", WorkflowNodeType.END, "结束",
                                map("outputs", List.of(out("summary", "{{agent}}"))))),
                List.of(
                        edge("e1", "start", "agent", "out"),
                        edge("e2", "agent", "end", "out")));
    }

    private static WorkflowDefinition conditionDefinition() {
        return new WorkflowDefinition(
                List.of(
                        node("start", WorkflowNodeType.START, "开始",
                                map("variables", List.of(var("score", 5)))),
                        node("cond", WorkflowNodeType.CONDITION, "评分判断",
                                map("left", "{{score}}", "operator", ">", "right", "10")),
                        node("high", WorkflowNodeType.AGENT, "高分处理", map("agentKey", "high")),
                        node("low", WorkflowNodeType.AGENT, "低分处理", map("agentKey", "low")),
                        node("end", WorkflowNodeType.END, "结束", map("outputs", List.of(out("taken", "done"))))),
                List.of(
                        edge("e1", "start", "cond", "out"),
                        edge("e2", "cond", "high", "true"),
                        edge("e3", "cond", "low", "false"),
                        edge("e4", "high", "end", "out"),
                        edge("e5", "low", "end", "out")));
    }

    private static WorkflowNode node(String id, WorkflowNodeType type, String name, Map<String, Object> config) {
        return new WorkflowNode(id, type, name, config);
    }

    private static WorkflowEdge edge(String id, String source, String target, String handle) {
        return new WorkflowEdge(id, source, target, handle);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> var(String name, Object def) {
        return map("name", name, "default", def);
    }

    private static Map<String, Object> out(String name, String value) {
        return map("name", name, "value", value);
    }

    private static final class FakeAgentRuntime implements AgentRuntime {
        final List<AgentRunBo> requests = new ArrayList<>();

        @Override
        public Flux<RuntimeRunEventDTO> stream(AgentRunBo request) {
            return Flux.empty();
        }

        @Override
        public Mono<AgentRunVo> call(AgentRunBo request) {
            requests.add(request);
            return Mono.just(new AgentRunVo("child-run", "answer:" + request.query()));
        }
    }

    private static final class InMemoryWorkflowRepository implements WorkflowRepository {
        private final List<Workflow> stored = new ArrayList<>();

        @Override
        public Workflow save(Workflow workflow) {
            var withId = new Workflow(1L, workflow.workflowKey(), workflow.name(), workflow.description(),
                    workflow.status(), workflow.draftDefinition(), workflow.publishedDefinition(),
                    workflow.publishedVersion(), workflow.publishedAt(), OffsetDateTime.now(), OffsetDateTime.now());
            stored.add(withId);
            return withId;
        }

        @Override
        public Workflow update(Workflow workflow) {
            for (int i = 0; i < stored.size(); i++) {
                if (stored.get(i).id().equals(workflow.id())) {
                    stored.set(i, workflow);
                    return workflow;
                }
            }
            return workflow;
        }

        @Override
        public Workflow findById(Long id) {
            return stored.stream().filter(w -> w.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public Workflow findByKey(String workflowKey) {
            return stored.stream().filter(w -> w.workflowKey().equals(workflowKey)).findFirst().orElse(null);
        }

        @Override
        public List<Workflow> list() {
            return stored;
        }

        @Override
        public void disable(Long id) {
            var workflow = findById(id);
            if (workflow != null) {
                stored.remove(workflow);
                stored.add(new Workflow(workflow.id(), workflow.workflowKey(), workflow.name(), workflow.description(),
                        WorkflowStatus.DISABLED, workflow.draftDefinition(), workflow.publishedDefinition(),
                        workflow.publishedVersion(), workflow.publishedAt(), workflow.createdAt(), workflow.updatedAt()));
            }
        }
    }

    private static final class InMemoryRunRepository implements WorkflowRunRepository {
        private final List<WorkflowRun> runs = new ArrayList<>();
        private final List<WorkflowNodeRun> nodeRuns = new ArrayList<>();

        @Override
        public void start(WorkflowRun run) {
            runs.add(run);
        }

        @Override
        public void complete(String runId, Map<String, Object> outputs, Instant finishedAt) {
            var run = findRun(runId);
            if (run != null) {
                runs.remove(run);
                runs.add(new WorkflowRun(run.runId(), run.workflowKey(), run.workflowVersion(),
                        WorkflowRunStatus.SUCCEEDED, run.inputs(), outputs, null, run.startedAt(), finishedAt, run.createdAt()));
            }
        }

        @Override
        public void fail(String runId, String error, Instant finishedAt) {
            var run = findRun(runId);
            if (run != null) {
                runs.remove(run);
                runs.add(new WorkflowRun(run.runId(), run.workflowKey(), run.workflowVersion(),
                        WorkflowRunStatus.FAILED, run.inputs(), run.outputs(), error, run.startedAt(), finishedAt, run.createdAt()));
            }
        }

        @Override
        public void saveNodeRun(WorkflowNodeRun nodeRun) {
            nodeRuns.add(nodeRun);
        }

        @Override
        public WorkflowRun findRun(String runId) {
            return runs.stream().filter(r -> r.runId().equals(runId)).findFirst().orElse(null);
        }

        @Override
        public List<WorkflowNodeRun> findNodeRuns(String runId) {
            return nodeRuns.stream().filter(n -> n.runId().equals(runId)).toList();
        }

        @Override
        public List<WorkflowRun> listRuns(String workflowKey) {
            return runs.stream().filter(r -> r.workflowKey().equals(workflowKey)).toList();
        }
    }
}
