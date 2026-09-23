package xin.v5ai.nb.rag.core.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParserHealthService {
    private final ParserServiceProperties properties;

    public Map<String, Map<String, Object>> check() {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        result.put("docling", checkOne(properties.getDocling().isEnabled(), properties.getDocling().getBaseUrl() + "/health",
                properties.getDocling().getApiKey()));
        result.put("mineru", checkOne(properties.getMineru().isEnabled(), properties.getMineru().getBaseUrl() + "/health",
                properties.getMineru().getHeaders()));
        result.put("default", Map.of("enabled", true, "available", true, "status", "UP"));
        return result;
    }

    private Map<String, Object> checkOne(boolean enabled, String url, Object auth) {
        if (!enabled) return Map.of("enabled", false, "available", false, "status", "DISABLED");
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET();
            if (auth instanceof String apiKey && apiKey != null && !apiKey.isBlank()) builder.header("X-Api-Key", apiKey);
            if (auth instanceof Map<?, ?> headers) headers.forEach((key, value) -> builder.header(String.valueOf(key), String.valueOf(value)));
            var response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.discarding());
            boolean up = response.statusCode() >= 200 && response.statusCode() < 300;
            return Map.of("enabled", true, "available", up, "status", up ? "UP" : "DOWN", "httpStatus", response.statusCode());
        } catch (Exception exception) {
            return Map.of("enabled", true, "available", false, "status", "DOWN");
        }
    }
}
