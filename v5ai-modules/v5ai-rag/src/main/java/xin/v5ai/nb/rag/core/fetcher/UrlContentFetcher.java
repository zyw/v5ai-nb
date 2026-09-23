package xin.v5ai.nb.rag.core.fetcher;

/**
 * URL 内容抓取端口：下载网页/远程文档的原始字节，用于知识库 URL 导入。
 * 实现应处理超时、编码识别与失败异常（如不可达、体积超限）。
 */
public interface UrlContentFetcher {
    /**
     * 下载 URL 并返回其原始字节与 Content-Type。
     *
     * @param url 目标地址
     * @return 下载到的内容（bytes 与 contentType 均可能为空）
     */
    UrlContent fetch(String url);
}