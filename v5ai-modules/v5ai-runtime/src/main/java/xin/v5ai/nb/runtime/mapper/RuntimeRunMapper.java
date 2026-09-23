package xin.v5ai.nb.runtime.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.core.enums.RunStatus;
import xin.v5ai.nb.runtime.domain.RuntimeRun;
import xin.v5ai.nb.runtime.domain.vo.RuntimeRunVo;

import java.time.OffsetDateTime;

@Mapper
public interface RuntimeRunMapper extends BaseMapperPlus<RuntimeRun, RuntimeRunVo> {

    /**
     * 标记运行完成，并写入收尾时间。
     *
     * <p>带 {@code WHERE status = 'RUNNING'} 条件：状态迁移是单向的，
     * 晚到的收尾动作（例如先点了停止、随后模型又答完）不得覆盖已落定的状态。
     */
    default boolean markCompleted(String runId) {
        return finish(runId, RunStatus.COMPLETED, null);
    }

    /**
     * 标记运行失败，并写入收尾时间与错误信息。
     */
    default boolean markFailed(String runId, String errorMessage) {
        return finish(runId, RunStatus.FAILED, errorMessage);
    }

    /**
     * 标记运行已取消：调用方主动停止（见 CONTEXT.md「停止」）。
     *
     * <p>不写 error_message——停止是正常的收尾动作，不是异常，
     * 用错误字段承载它会让「运行失败」的查询把主动停止算进去。
     */
    default boolean markCanceled(String runId) {
        return finish(runId, RunStatus.CANCELED, null);
    }

    /**
     * 收尾写入的公共路径：状态 + 结束时间（+ 可选错误信息），
     * 且只在当前仍是 RUNNING 时生效。
     */
    private boolean finish(String runId, RunStatus status, String errorMessage) {
        var entity = new RuntimeRun();
        entity.setStatus(status.name());
        entity.setCompletedAt(OffsetDateTime.now());
        entity.setErrorMessage(errorMessage);
        return update(entity, new LambdaUpdateWrapper<RuntimeRun>()
                .eq(RuntimeRun::getId, runId)
                .eq(RuntimeRun::getStatus, RunStatus.RUNNING.name())) > 0;
    }
}
