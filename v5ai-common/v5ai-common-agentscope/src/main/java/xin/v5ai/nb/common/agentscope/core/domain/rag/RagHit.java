package xin.v5ai.nb.common.agentscope.core.domain.rag;

/**
 * 一条知识检索命中（引用展示用）。
 *
 * @param knowledgeBaseId 来源知识库 ID
 * @param documentId      来源文档 ID
 * @param documentTitle   来源文档标题（未落库的文档回退为 doc-{id}）
 * @param chunkIndex      切片序号（文档内序号，命中定位用）
 * @param content         切片内容（上游按长度截断，避免 SSE 载荷过大）
 * @param score           相似度得分（越高越相关）
 */
public record RagHit(
        Long knowledgeBaseId,
        Long documentId,
        String documentTitle,
        Integer chunkIndex,
        String content,
        Double score
) {
}
