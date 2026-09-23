package xin.v5ai.nb.rag.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.rag.domain.KnowledgeTask;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;

import java.util.List;

/**
 * <p>
 * 知识库索引任务 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface KnowledgeTaskMapper extends BaseMapperPlus<KnowledgeTask, KnowledgeTaskVo> {

    /**
     * 查询待处理任务：PENDING 任务，以及仍有余量尝试次数的 FAILED 任务（按 id 升序，限量）。
     */
    List<KnowledgeTask> selectProcessable(@Param("limit") int limit);

    /**
     * 回收卡死在 PROCESSING 状态的任务（进程崩溃/重启遗留）：距最近更新超过 {@code staleMinutes}
     * 分钟的任务，尝试次数未用完则重置为 PENDING 重试，已用完则置为 FAILED 并写明原因。
     *
     * @param staleMinutes 判定「陈旧」的分钟阈值
     * @return 被回收的任务数
     */
    int recoverStaleProcessing(@Param("staleMinutes") int staleMinutes);
}
