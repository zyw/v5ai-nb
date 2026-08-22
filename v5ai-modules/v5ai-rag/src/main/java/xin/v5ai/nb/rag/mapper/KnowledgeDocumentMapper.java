package xin.v5ai.nb.rag.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;

/**
 * <p>
 * 知识库文档 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapperPlus<KnowledgeDocument, KnowledgeDocumentVo> {
}
