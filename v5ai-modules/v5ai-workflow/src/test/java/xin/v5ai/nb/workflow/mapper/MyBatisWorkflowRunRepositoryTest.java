package xin.v5ai.nb.workflow.mapper;

import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.workflow.core.WorkflowRun;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;
import xin.v5ai.nb.workflow.domain.Workflow;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisWorkflowRunRepositoryTest {

    @Test
    void startLocksAndRevalidatesWorkflowBeforeInsertingRun() {
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        WorkflowRunMapper runMapper = mock(WorkflowRunMapper.class);
        WorkflowNodeRunMapper nodeRunMapper = mock(WorkflowNodeRunMapper.class);
        when(workflowMapper.selectForRunAdmission("wf-1")).thenReturn(new Workflow());
        var repository = new MyBatisWorkflowRunRepository(runMapper, nodeRunMapper, workflowMapper);
        WorkflowRun run = runningRun();

        repository.start(run);

        InOrder order = inOrder(workflowMapper, runMapper);
        order.verify(workflowMapper).selectForRunAdmission("wf-1");
        order.verify(runMapper).insert(any(xin.v5ai.nb.workflow.domain.WorkflowRun.class));
    }

    @Test
    void startDoesNotInsertRunIfWorkflowWasDeletedBeforeAdmission() {
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        WorkflowRunMapper runMapper = mock(WorkflowRunMapper.class);
        WorkflowNodeRunMapper nodeRunMapper = mock(WorkflowNodeRunMapper.class);
        when(workflowMapper.selectForRunAdmission("wf-1")).thenReturn(null);
        var repository = new MyBatisWorkflowRunRepository(runMapper, nodeRunMapper, workflowMapper);

        assertThatThrownBy(() -> repository.start(runningRun()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflow does not exist");
        verify(runMapper, never()).insert(any(xin.v5ai.nb.workflow.domain.WorkflowRun.class));
    }

    private WorkflowRun runningRun() {
        Instant now = Instant.now();
        return new WorkflowRun("run-1", "wf-1", 1L, WorkflowRunStatus.RUNNING,
                Map.of(), null, null, now, null, now);
    }
}
