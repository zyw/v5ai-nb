package xin.v5ai.nb.rag.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.KnowledgeChunkDO;
import xin.v5ai.nb.rag.domain.vo.KnowledgeChunkVo;

import java.util.List;

/**
 * <p>
 * 知识库分片 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-09-03
 */
public interface KnowledgeChunkMapper extends BaseMapperPlus<KnowledgeChunk, KnowledgeChunkVo> {

    /**
     * 计算某文档的下一个切片序号（当前最大值 + 1；无切片时为 0）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @return 下一个 chunkIndex
     */
    @Select("SELECT COALESCE(MAX(chunk_index), -1) + 1 FROM v5ai_knowledge_chunk " +
            "WHERE knowledge_base_id = #{knowledgeBaseId} AND document_id = #{documentId}")
    int nextChunkIndex(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("documentId") Long documentId);
    /**
     * 根据知识库id列表查询分片
     * @param embed1 嵌入向量1
     * @param ids 知识库id列表
     * @param embed2 嵌入向量2
     * @param limit 限制数量
     * @return 查询结果
     */
    List<KnowledgeChunkDO> findChunkByBaseIds(
            @Param("embed1") String embed1,
            @Param("ids") List<Long> ids,
            @Param("embed2") String embed2,
            @Param("limit") int limit);

    /**
     * 根据知识库id列表查询分片
     * @param ids 知识库id列表
     * @param keyword 关键字
     * @param limit 限制数量
     * @return 查询结果
     */
    List<KnowledgeChunkDO> findChunkByIdsAndLinkKeyword(
            @Param("ids") List<Long> ids,
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
