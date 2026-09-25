package xin.v5ai.nb.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.workflow.domain.WorkflowVersion;

@Mapper
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersion> {
}
