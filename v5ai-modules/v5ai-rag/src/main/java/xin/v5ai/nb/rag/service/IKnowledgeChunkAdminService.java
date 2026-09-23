package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.domain.bo.KbChunkAddBo;
import xin.v5ai.nb.rag.domain.bo.KbChunkEditBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeChunkVo;

/**
 * 知识库切片管理（调试/管理端）：分页浏览、手工新增、编辑与删除。
 * <p>
 * 手工切片的写操作与 Worker 索引走同一条向量入库链路（Embedding → VectorStore），
 * 保证同一知识库内向量口径一致。
 *
 * @author ZYW
 * @since 2026-09-06
 */
public interface IKnowledgeChunkAdminService {

    /**
     * 分页查询某知识库的切片（附来源文档标题）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档过滤（可为空 = 全部文档）
     * @param chunkId         切片 ID 过滤（可为空）
     * @param content         内容模糊过滤（可为空）
     * @param pageQuery       分页
     * @return 切片分页
     */
    PageResult<KnowledgeChunkVo> listChunks(Long knowledgeBaseId, Long documentId, Long chunkId,
                                            String content, PageQuery pageQuery);

    /**
     * 统计某知识库切片数（详情页头部统计用）。
     */
    long countByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 手工新增切片：追加到目标文档末尾并同步向量入库。
     */
    KnowledgeChunkVo addChunk(Long knowledgeBaseId, KbChunkAddBo bo);

    /**
     * 编辑切片内容并重新嵌入。
     */
    KnowledgeChunkVo updateChunk(Long knowledgeBaseId, Long chunkId, KbChunkEditBo bo);

    /**
     * 删除单个切片（连带其向量记录）。
     */
    void deleteChunk(Long knowledgeBaseId, Long chunkId);
}
