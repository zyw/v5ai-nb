package xin.v5ai.nb.workflow.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.workflow.domain.Workflow;
import xin.v5ai.nb.workflow.domain.vo.WorkflowVo;

import java.util.List;

/**
 * <p>
 * Workflow Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface WorkflowMapper extends BaseMapperPlus<Workflow, WorkflowVo> {

    default List<Workflow> selectBatchForUpdate(List<String> workflowKeys) {
        return selectList(new QueryWrapper<Workflow>()
                .in("workflow_key", workflowKeys)
                .last("FOR UPDATE"));
    }

    default Workflow selectForRunAdmission(String workflowKey) {
        return selectOne(new QueryWrapper<Workflow>()
                .eq("workflow_key", workflowKey)
                .last("FOR UPDATE"));
    }
}
