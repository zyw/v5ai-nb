package xin.v5ai.nb.rag.core.fetcher;

/**
 * URL 内容抓取端口：抓取网页/远程文档的文本内容，用于知识库 URL 导入。
 * 实现应处理超时、编码识别与失败异常（如不可达、非文本内容）。
 */
public interface UrlContentFetcher {
    /**
     * 抓取 URL 并返回其文本内容。
     *
     * @param url 目标地址
     * @return 抓取到的文本
     */
    String fetch(String url);
}
