package xin.v5ai.nb.rag.core.fetcher;

/**
 * URL 下载结果：原始字节 + 响应 Content-Type（可能为 null）。
 * 由 {@link UrlContentFetcher} 返回，供导入方按类型分发解析。
 */
public record UrlContent(byte[] bytes, String contentType) {
}