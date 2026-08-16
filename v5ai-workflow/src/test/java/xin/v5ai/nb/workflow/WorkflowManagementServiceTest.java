package xin.v5ai.nb.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowManagementServiceTest {

    @Test
    void createAssignsIdAndDraftStatus() {
        var service = service();
        var workflow = service.create("ticket", "Ticket Bot", "handle tickets");

        assertThat(workflow.id()).isNotNull();
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.DRAFT);
        assertThat(workflow.publishedVersion()).isNull();
    }

    @Test
    void createRejectsBlankKeyOrName() {
        var service = service();
        assertThatThrownBy(() -> service.create(" ", "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflowKey");
        assertThatThrownBy(() -> service.create("key", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void createRejectsDuplicateKey() {
        var service = service();
        service.create("ticket", "Ticket", null);
        assertThatThrownBy(() -> service.create("ticket", "Ticket 2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void publishIncrementsVersionAndSnapshotsDraft() {
        var service = service();
        service.create("ticket", "Ticket", null);
        service.update("ticket", null, null, linearDefinition());
        var published = service.publish("ticket");

        assertThat(published.status()).isEqualTo(WorkflowStatus.PUBLISHED);
        assertThat(published.publishedVersion()).isEqualTo(1L);
        assertThat(published.publishedDefinition()).isEqualTo(linearDefinition());

        // 再次发布同一草稿，版本号递增
        var republished = service.publish("ticket");
        assertThat(republished.publishedVersion()).isEqualTo(2L);
    }

    @Test
    void publishRejectsWhenNoDraft() {
        var service = service();
        service.create("ticket", "Ticket", null);
        assertThatThrownBy(() -> service.publish("ticket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no draft");
    }

    @Test
    void publishRejectsDefinitionWithoutStart() {
        var service = service();
        service.create("ticket", "Ticket", null);
        service.update("ticket", null, null, new WorkflowDefinition(
                List.of(node("end", WorkflowNodeType.END, "结束", Map.of())),
                List.of()));
        assertThatThrownBy(() -> service.publish("ticket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("START");
    }

    @Test
    void publishRejectsCyclicDefinition() {
        var service = service();
        service.create("ticket", "Ticket", null);
        service.update("ticket", null, null, new WorkflowDefinition(
                List.of(
                        node("start", WorkflowNodeType.START, "开始", Map.of()),
                        node("a", WorkflowNodeType.AGENT, "A", Map.of("agentKey", "demo")),
                        node("end", WorkflowNodeType.END, "结束", Map.of())),
                List.of(
                        edge("e1", "start", "a", "out"),
                        edge("e2", "a", "start", "out"),
                        edge("e3", "a", "end", "out"))));
        assertThatThrownBy(() -> service.publish("ticket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void disablePreventsUpdateAndPublish() {
        var service = service();
        service.create("ticket", "Ticket", null);
        service.update("ticket", null, null, linearDefinition());
        service.publish("ticket");
        service.disable("ticket");

        assertThat(service.get("ticket").status()).isEqualTo(WorkflowStatus.DISABLED);
        assertThatThrownBy(() -> service.update("ticket", "x", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
        assertThatThrownBy(() -> service.publish("ticket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    // ---- helpers ----

    private static WorkflowManagementService service() {
        return new WorkflowManagementService(new InMemoryWorkflowRepository(), new InMemoryRunRepository());
    }

    private static WorkflowDefinition linearDefinition() {
        return new WorkflowDefinition(
                List.of(
                        node("start", WorkflowNodeType.START, "开始", map("variables", List.of(var("topic", "weather")))),
                        node("agent", WorkflowNodeType.AGENT, "总结", map("agentKey", "summarizer", "prompt", "{{topic}}")),
                        node("end", WorkflowNodeType.END, "结束", map("outputs", List.of(out("summary", "{{agent}}"))))),
                List.of(
                        edge("e1", "start", "agent", "out"),
                        edge("e2", "agent", "end", "out")));
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

    private static final class InMemoryWorkflowRepository implements WorkflowRepository {
        private final List<Workflow> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Workflow save(Workflow workflow) {
            var withId = new Workflow(nextId++, workflow.workflowKey(), workflow.name(), workflow.description(),
                    workflow.status(), workflow.draftDefinition(), workflow.publishedDefinition(),
                    workflow.publishedVersion(), workflow.publishedAt(), Instant.now(), Instant.now());
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
