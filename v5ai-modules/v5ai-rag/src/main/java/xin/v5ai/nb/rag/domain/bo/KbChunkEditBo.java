package xin.v5ai.nb.rag.domain.bo;

/**
 * 编辑切片请求体（不可变 record）：修改内容后重新嵌入。
 *
 * @param content 新的切片内容
 */
public record KbChunkEditBo(String content) {
}
