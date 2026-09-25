package xin.v5ai.nb.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import xin.v5ai.nb.workflow.core.WorkflowEngine;
import xin.v5ai.nb.workflow.domain.bo.WorkflowBatchDeleteBo;
import xin.v5ai.nb.workflow.service.IWorkflowService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkflowControllerTest {

    @Test
    void batchDeleteDelegatesKeysAndRequiresRemovePermission() throws Exception {
        IWorkflowService service = mock(IWorkflowService.class);
        WorkflowController controller = new WorkflowController(service, mock(WorkflowEngine.class));
        WorkflowBatchDeleteBo request = new WorkflowBatchDeleteBo();
        request.setWorkflowKeys(List.of("draft-a", "disabled-b"));

        var response = controller.deleteBatch(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(service).deleteBatch(List.of("draft-a", "disabled-b"));
        var method = WorkflowController.class.getMethod("deleteBatch", WorkflowBatchDeleteBo.class);
        assertThat(method.getAnnotation(DeleteMapping.class).value()).containsExactly("/batch");
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
                .containsExactly("workflow:workflow:remove");
    }

    @Test
    void legacyDeleteEndpointStillDisablesWorkflow() throws Exception {
        IWorkflowService service = mock(IWorkflowService.class);
        WorkflowController controller = new WorkflowController(service, mock(WorkflowEngine.class));

        controller.disable("legacy-key");

        verify(service).disable("legacy-key");
        var method = WorkflowController.class.getMethod("disable", String.class);
        assertThat(method.getAnnotation(DeleteMapping.class).value()).containsExactly("/{key}");
    }
}
