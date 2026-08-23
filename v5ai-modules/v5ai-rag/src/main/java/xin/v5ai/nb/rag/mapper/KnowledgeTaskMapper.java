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
}
