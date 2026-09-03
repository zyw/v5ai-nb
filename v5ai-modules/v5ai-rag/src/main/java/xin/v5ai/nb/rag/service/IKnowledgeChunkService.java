package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.KnowledgeChunkDO;

import java.util.List;

public interface IKnowledgeChunkService {

    /**
     * 批量保存知识块
     * @param knowledgeChunks 知识块列表
     * @return 是否保存成功
     */
    boolean saveBatch(List<KnowledgeChunk> knowledgeChunks);

    /**
     * 根据文档ID删除知识块
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long knowledgeBaseId, Long documentId);

    /**
     * 根据知识库ID列表查询知识块
     * @param embed1 嵌入向量1
     * @param embed2 嵌入向量2
     * @param limit 限制数量
     * @return 知识块列表
     */
    List<KnowledgeChunkDO> findChunkByBaseIds(String embed1, List<Long> ids, String embed2, int limit);

    /**
     * 根据ID列表和链接内容查询知识块
     * @param ids ID列表
      * @param keyword 链接内容
     * @param limit 限制数量
     * @return 知识块列表
     */
    List<KnowledgeChunkDO> findChunkByIdsAndLinkKeyword(List<Long> ids, String keyword, int limit);
}
