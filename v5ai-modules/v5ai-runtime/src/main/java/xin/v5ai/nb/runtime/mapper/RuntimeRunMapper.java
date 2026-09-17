package xin.v5ai.nb.runtime.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.RuntimeRun;
import xin.v5ai.nb.runtime.domain.vo.RuntimeRunVo;

@Mapper
public interface RuntimeRunMapper extends BaseMapperPlus<RuntimeRun, RuntimeRunVo> {

    default boolean markCompleted(String runId) {
        var entity = new RuntimeRun();
        entity.setStatus("COMPLETED");
        return update(entity, new
                LambdaUpdateWrapper<RuntimeRun>().eq(RuntimeRun::getId, runId)) > 0;
    }

    default boolean markFailed(String runId, String errorMessage) {
        var entity = new RuntimeRun();
        entity.setStatus("FAILED");
        entity.setErrorMessage(errorMessage);
        return update(entity,
                new LambdaUpdateWrapper<RuntimeRun>().eq(RuntimeRun::getId, runId)) > 0;
    }
}
