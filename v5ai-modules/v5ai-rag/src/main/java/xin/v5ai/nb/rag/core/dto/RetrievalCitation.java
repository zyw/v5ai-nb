package xin.v5ai.nb.rag.core.dto;

/**
 * 一条引用的展示信息（运行期检索命中 + 文档标题）。
 *
 * <p>标题在 {@code RetrievalContextBuilder.formatContext} 组装上下文时顺带查出，
 * 与上下文同源，不额外增加查询。</p>
 *
 * @param knowledgeBaseId 来源知识库 ID
 * @param documentId      来源文档 ID
 * @param documentTitle   来源文档标题（文档行缺失时回退为 doc-{id}）
 * @param chunkIndex      切片序号
 * @param content         切片内容
 * @param score           相似度得分
 */
public record RetrievalCitation(
        Long knowledgeBaseId,
        Long documentId,
        String documentTitle,
        Integer chunkIndex,
        String content,
        Double score
) {
}
