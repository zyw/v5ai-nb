package xin.v5ai.nb.workflow.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.workflow.domain.Workflow;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class WorkflowMapperTest {

    @Test
    void selectBatchForUpdateBuildsParameterizedKeyFilterAndRowLock() {
        WorkflowMapper mapper = mock(WorkflowMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectBatchForUpdate(List.of("wf-a", "wf-b"));

        ArgumentCaptor<Wrapper<Workflow>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        QueryWrapper<Workflow> query = (QueryWrapper<Workflow>) wrapper.getValue();
        assertThat(query.getCustomSqlSegment())
                .contains("workflow_key")
                .contains("FOR UPDATE");
        assertThat(query.getParamNameValuePairs().values())
                .contains("wf-a", "wf-b");
    }

    @Test
    void selectForRunAdmissionBuildsParameterizedWorkflowLock() {
        WorkflowMapper mapper = mock(WorkflowMapper.class, CALLS_REAL_METHODS);
        doReturn(null).when(mapper).selectOne(any(Wrapper.class));

        mapper.selectForRunAdmission("wf-a");

        ArgumentCaptor<Wrapper<Workflow>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(wrapper.capture());
        QueryWrapper<Workflow> query = (QueryWrapper<Workflow>) wrapper.getValue();
        assertThat(query.getCustomSqlSegment())
                .contains("workflow_key")
                .contains("FOR UPDATE");
        assertThat(query.getParamNameValuePairs().values()).contains("wf-a");
    }
}
