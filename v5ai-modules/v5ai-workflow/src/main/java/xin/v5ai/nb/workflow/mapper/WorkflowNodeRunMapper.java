package xin.v5ai.nb.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.workflow.domain.WorkflowNodeRun;

/**
 * <p>
 * Workflow 节点运行 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRun> {
}
