package xin.v5ai.nb.rag.domain.bo;

/**
 * 手工新增切片请求体（不可变 record）。
 *
 * @param documentId 目标文档 ID（必须属于路径中的知识库）
 * @param content    切片内容（非空，长度受服务端上限约束）
 */
public record KbChunkAddBo(Long documentId, String content) {
}
