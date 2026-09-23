package xin.v5ai.nb.rag.core.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "v5ai.rag.parser")
public class ParserServiceProperties {
    private Docling docling = new Docling();
    private Mineru mineru = new Mineru();

    @Data
    public static class Docling {
        private boolean enabled;
        private String baseUrl = "http://127.0.0.1:5001";
        private String apiKey;
        private long connectTimeoutMillis = 5000;
        private long readTimeoutMillis = 600000;
        private long pollIntervalMillis = 2000;
        private long pollTimeoutMillis = 1800000;
        private int concurrency = 3;
    }

    @Data
    public static class Mineru {
        private boolean enabled;
        private String baseUrl = "http://127.0.0.1:8000";
        private Map<String, String> headers = new LinkedHashMap<>();
        private long connectTimeoutMillis = 5000;
        private long readTimeoutMillis = 600000;
        private long pollIntervalMillis = 3000;
        private long pollTimeoutMillis = 1800000;
    }
}
