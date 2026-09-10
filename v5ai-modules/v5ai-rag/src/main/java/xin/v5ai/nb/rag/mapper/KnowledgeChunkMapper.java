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

    boolean batchSaveChunks(@Param("chunks") List<KnowledgeChunk> knowledgeChunks);

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
     * 按向量标识批量回查业务行（向量检索命中后回业务表取内容/元数据）。
     *
     * @param vectorIds 向量标识（集合主键）列表
     * @return 业务行列表
     */
    List<KnowledgeChunkDO> selectByVectorIds(@Param("vectorIds") List<String> vectorIds);

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

    /**
     * BM25 关键词候选召回：GIN 索引取「分词数组与查询词项有交集」的切片行
     * （{@code keyword_tokens && terms}），按命中词覆盖度粗排后截断；
     * 候选集的真实相关度排序由 BM25 计分侧决定，故候选数应大于返回条数。
     *
     * @param ids   限定的知识库 ID 集合
     * @param terms 查询词项（分词后去重，作为 {@code text[]} 绑定）
     * @param limit 候选条数上限
     * @return 候选切片行（含 {@code keyword_tokens}，用于现算词频与文档长度）
     */
    List<KnowledgeChunkDO> findChunksByKeywordTokens(@Param("ids") List<Long> ids,
                                                     @Param("terms") String[] terms,
                                                     @Param("limit") int limit);

    /**
     * 已建立分词索引的切片行数（BM25 的语料规模 N；未重建/无有效分词的行不计入）。
     *
     * @param ids 限定的知识库 ID 集合
     * @return 行数
     */
    long countIndexedChunks(@Param("ids") List<Long> ids);

    /**
     * 平均文档长度 avgdl（分词数组元素个数均值；无已索引行时为 0）。
     *
     * @param ids 限定的知识库 ID 集合
     * @return 平均长度
     */
    double avgKeywordTokenLength(@Param("ids") List<Long> ids);

    /**
     * 词项文档频率 df：包含该词项的切片行数（BM25 的 idf 用）。
     *
     * @param ids  限定的知识库 ID 集合
     * @param term 单个查询词项
     * @return 行数
     */
    long countChunksByKeywordToken(@Param("ids") List<Long> ids, @Param("term") String term);
}
