package xin.v5ai.nb.rag.domain.vo;

/**
 * 检索命中结果（不可变 record），供知识检索调试与问答引用展示。
 *
 * @param knowledgeBaseId 知识库 ID
 * @param documentId      来源文档 ID
 * @param documentTitle   来源文档标题
 * @param chunkIndex      切片序号（文档内序号，命中定位用）
 * @param content         切片内容
 * @param score           相似度得分（向量路 0~1；RRF 融合后为融合分）
 */
public record KbHitVo(
        Long knowledgeBaseId,
        Long documentId,
        String documentTitle,
        Integer chunkIndex,
        String content,
        Double score
) {
}
