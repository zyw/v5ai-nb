package xin.v5ai.nb.rag.domain.bo;

/**
 * URL 导入请求体。
 *
 * @param url   目标 URL（必填）
 * @param title 文档标题（可选，缺省用 URL）
 */
public record ImportUrlBo(String url, String title) {
}
