package xin.v5ai.nb.rag;

/**
 * Fetches the text content of a URL for knowledge base import.
 */
public interface UrlContentFetcher {
    String fetch(String url);
}
