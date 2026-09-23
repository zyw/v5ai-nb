package xin.v5ai.nb.rag.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;

/**
 * <p>
 * 知识库 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapperPlus<KnowledgeBase, KnowledgeBaseVo> {
}
