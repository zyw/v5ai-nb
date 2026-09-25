package xin.v5ai.nb.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.workflow.core.WorkflowDefinition;
import xin.v5ai.nb.workflow.core.WorkflowEdge;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.domain.Workflow;
import xin.v5ai.nb.workflow.domain.WorkflowNodeRun;
import xin.v5ai.nb.workflow.domain.WorkflowRun;
import xin.v5ai.nb.workflow.domain.WorkflowVersion;
import xin.v5ai.nb.workflow.domain.bo.WorkflowBo;
import xin.v5ai.nb.workflow.mapper.WorkflowMapper;
import xin.v5ai.nb.workflow.mapper.WorkflowNodeRunMapper;
import xin.v5ai.nb.workflow.mapper.WorkflowRunMapper;
import xin.v5ai.nb.workflow.mapper.WorkflowVersionMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowServiceImplTest {

    private final List<Workflow> workflows = new ArrayList<>();
    private final List<WorkflowRun> runs = new ArrayList<>();
    private final List<WorkflowNodeRun> nodeRuns = new ArrayList<>();
    private WorkflowVersionMapper versionMapper;

    private WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        WorkflowRunMapper runMapper = mock(WorkflowRunMapper.class);
        WorkflowNodeRunMapper nodeRunMapper = mock(WorkflowNodeRunMapper.class);
        versionMapper = mock(WorkflowVersionMapper.class);

        when(workflowMapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> workflows.stream().findFirst().orElse(null));
        when(workflowMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(workflows));
        when(workflowMapper.selectById(anyLong())).thenAnswer(inv -> null);
        doAnswer(inv -> {
            var workflow = inv.getArgument(0, Workflow.class);
            workflow.setId((long) (workflows.size() + 1));
            workflows.add(workflow);
            return 1;
        }).when(workflowMapper).insert(any(Workflow.class));
        doAnswer(inv -> {
            Workflow update = inv.getArgument(0, Workflow.class);
            workflows.stream().filter(w -> w.getId().equals(update.getId())).forEach(w -> {
                if (update.getStatus() != null) {
                    w.setStatus(update.getStatus());
                }
                if (update.getName() != null) {
                    w.setName(update.getName());
                }
                if (update.getDescription() != null) {
                    w.setDescription(update.getDescription());
                }
                if (update.getDraftDefinitionJson() != null) {
                    w.setDraftDefinitionJson(update.getDraftDefinitionJson());
                }
                if (update.getPublishedDefinitionJson() != null) {
                    w.setPublishedDefinitionJson(update.getPublishedDefinitionJson());
                }
                if (update.getPublishedVersion() != null) {
                    w.setPublishedVersion(update.getPublishedVersion());
                }
                if (update.getPublishedAt() != null) {
                    w.setPublishedAt(update.getPublishedAt());
                }
            });
            return 1;
        }).when(workflowMapper).updateById(any(Workflow.class));

        when(runMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(runs));
        when(runMapper.selectById(any())).thenAnswer(inv -> runs.stream()
                .filter(r -> r.getRunId().equals(inv.getArgument(0))).findFirst().orElse(null));
        doAnswer(inv -> {
            runs.add(inv.getArgument(0, WorkflowRun.class));
            return 1;
        }).when(runMapper).insert(any(WorkflowRun.class));

        when(nodeRunMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(nodeRuns));

        service = new WorkflowServiceImpl(workflowMapper, runMapper, nodeRunMapper);
        service.setVersionMapper(versionMapper);
    }

    @Test
    void createMakesDraft() {
        var bo = new WorkflowBo();
        bo.setWorkflowKey("wf-demo");
        bo.setName("Demo");

        var vo = service.create(bo);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getStatus()).isEqualTo("DRAFT");
        assertThat(service.get("wf-demo").getWorkflowKey()).isEqualTo("wf-demo");
    }

    @Test
    void createRejectsDuplicateKey() {
        var bo = new WorkflowBo();
        bo.setWorkflowKey("wf-demo");
        bo.setName("Demo");
        service.create(bo);

        var dup = new WorkflowBo();
        dup.setWorkflowKey("wf-demo");
        dup.setName("Dup");
        assertThatThrownBy(() -> service.create(dup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateSavesDraftDefinition() {
        service.create(bo("wf-demo", "Demo"));
        var update = new WorkflowBo();
        update.setName("Demo 2");
        update.setDefinition(definition());

        var vo = service.update("wf-demo", update);

        assertThat(vo.getName()).isEqualTo("Demo 2");
        assertThat(vo.getDraftDefinition()).isNotNull();
        assertThat(vo.getDraftDefinition().nodes()).hasSize(2);
    }

    @Test
    void publishValidatesAndSnapshotsDraft() {
        service.create(bo("wf-demo", "Demo"));
        service.update("wf-demo", updateWithDefinition());

        var published = service.publish("wf-demo");

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedVersion()).isEqualTo(1L);
        assertThat(published.getPublishedDefinition()).isNotNull();
    }

    @Test
    void publishRequiresDraftDefinition() {
        service.create(bo("wf-demo", "Demo"));

        assertThatThrownBy(() -> service.publish("wf-demo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no draft definition");
    }

    @Test
    void disableBlocksPublish() {
        service.create(bo("wf-demo", "Demo"));
        service.update("wf-demo", updateWithDefinition());
        service.publish("wf-demo");
        service.disable("wf-demo");

        assertThatThrownBy(() -> service.publish("wf-demo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void enableRestoresPublishedWorkflow() {
        service.create(bo("wf-demo", "Demo"));
        service.update("wf-demo", updateWithDefinition());
        service.publish("wf-demo");
        service.disable("wf-demo");

        service.enable("wf-demo");

        assertThat(service.get("wf-demo").getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void enableRestoresDraftWorkflow() {
        service.create(bo("wf-demo", "Demo"));
        service.disable("wf-demo");

        service.enable("wf-demo");

        assertThat(service.get("wf-demo").getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void listRunsAndNodeRuns() {
        service.create(bo("wf-demo", "Demo"));
        var run = new WorkflowRun();
        run.setRunId("r1");
        run.setWorkflowKey("wf-demo");
        runs.add(run);
        var node = new WorkflowNodeRun();
        node.setRunId("r1");
        nodeRuns.add(node);

        assertThat(service.listRuns("wf-demo")).hasSize(1);
        assertThat(service.getRun("r1").runId()).isEqualTo("r1");
        assertThat(service.getNodeRuns("r1")).hasSize(1);
    }

    @Test
    void runDetailIncludesNodeNameFromDraftRunSnapshot() {
        var run = new WorkflowRun();
        run.setRunId("draft-run");
        run.setWorkflowKey("wf-demo");
        run.setSource("DRAFT_TEST");
        run.setDefinitionSnapshot(xin.v5ai.nb.workflow.core.WorkflowJson.toJson(
                new WorkflowDefinition(List.of(new WorkflowNode("node-1", WorkflowNodeType.START,
                        "接收订单", null)), List.of())));
        runs.add(run);
        var nodeRun = new WorkflowNodeRun();
        nodeRun.setRunId("draft-run");
        nodeRun.setNodeId("node-1");
        nodeRun.setNodeType("START");
        nodeRuns.add(nodeRun);

        var detail = service.getRunDetail("draft-run");

        assertThat(detail.nodeRuns()).singleElement()
                .extracting("nodeName").isEqualTo("接收订单");
        assertThat(detail.nodeRuns().getFirst().nodeId()).isEqualTo("node-1");
    }

    @Test
    void runDetailUsesPublishedVersionSnapshotForNodeName() {
        var run = new WorkflowRun();
        run.setRunId("published-run");
        run.setWorkflowKey("wf-demo");
        run.setWorkflowVersion(2L);
        run.setSource("PUBLISHED");
        runs.add(run);
        var nodeRun = new WorkflowNodeRun();
        nodeRun.setRunId("published-run");
        nodeRun.setNodeId("node-1");
        nodeRun.setNodeType("START");
        nodeRuns.add(nodeRun);
        var version = new WorkflowVersion();
        version.setDefinition(xin.v5ai.nb.workflow.core.WorkflowJson.toJson(
                new WorkflowDefinition(List.of(new WorkflowNode("node-1", WorkflowNodeType.START,
                        "发布时的名称", null)), List.of())));
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(version);

        var detail = service.getRunDetail("published-run");

        assertThat(detail.nodeRuns()).singleElement()
                .extracting("nodeName").isEqualTo("发布时的名称");
    }

    private static WorkflowBo bo(String key, String name) {
        var bo = new WorkflowBo();
        bo.setWorkflowKey(key);
        bo.setName(name);
        return bo;
    }

    private static WorkflowBo updateWithDefinition() {
        var bo = new WorkflowBo();
        bo.setDefinition(definition());
        return bo;
    }

    private static WorkflowDefinition definition() {
        return new WorkflowDefinition(
                List.of(new WorkflowNode("n1", WorkflowNodeType.START, null, null),
                        new WorkflowNode("n2", WorkflowNodeType.END, null, null)),
                List.of(new WorkflowEdge("e1", "n1", "n2", null)));
    }
}
