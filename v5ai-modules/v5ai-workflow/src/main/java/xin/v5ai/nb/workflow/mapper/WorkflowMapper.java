package xin.v5ai.nb.workflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.workflow.domain.Workflow;
import xin.v5ai.nb.workflow.domain.vo.WorkflowVo;

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
}
