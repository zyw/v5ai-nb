package xin.v5ai.nb.rag.core.fetcher;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpUrlContentFetcher implements UrlContentFetcher {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public UrlContent fetch(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("url must be http(s): " + url);
        }
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "v5ai-nb-knowledge-bot/0.1")
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("url fetch failed with status " + response.statusCode());
            }
            var bytes = response.body();
            if (bytes.length > MAX_BYTES) {
                throw new IllegalStateException("url content exceeds " + MAX_BYTES + " bytes");
            }
            return new UrlContent(bytes, response.headers().firstValue("Content-Type").orElse(null));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("url fetch interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("url fetch failed: " + url, exception);
        }
    }
}