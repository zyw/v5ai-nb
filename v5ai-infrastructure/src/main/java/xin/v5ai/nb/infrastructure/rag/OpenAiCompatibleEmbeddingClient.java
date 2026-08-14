package xin.v5ai.nb.infrastructure.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.infrastructure.model.ModelRuntimeConfigGateway;
import xin.v5ai.nb.infrastructure.runtime.AgentModelCredentialConfig;
import xin.v5ai.nb.model.CredentialCipher;
import xin.v5ai.nb.rag.EmbeddingClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedding client over an OpenAI-compatible {@code /embeddings} endpoint, resolved
 * lazily from the first enabled EMBEDDING model. When no EMBEDDING model is
 * configured it falls back to a deterministic local hash embedding so the RAG
 * pipeline stays runnable in development.
 */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    public static final int DIMENSIONS = 1536;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModelRuntimeConfigGateway runtimeConfigGateway;
    private final CredentialCipher credentialCipher;

    public OpenAiCompatibleEmbeddingClient(ModelRuntimeConfigGateway runtimeConfigGateway, CredentialCipher credentialCipher) {
        this.runtimeConfigGateway = runtimeConfigGateway;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public List<Float> embed(String text) {
        var model = findEmbeddingModel();
        if (model == null) {
            return HashEmbedding.embed(text, DIMENSIONS);
        }
        var credentials = AgentModelCredentialConfig.parse(credentialCipher.decrypt(model.credentialsCiphertext()));
        var baseUrl = credentials.baseUrl() == null || credentials.baseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : credentials.baseUrl().replaceAll("/+$", "");
        var requestBody = """
                {"model":"%s","input":"%s"}
                """.formatted(model.modelKey(), escape(text));
        try {
            var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (credentials.apiKey() == null ? "" : credentials.apiKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("embedding request failed with status " + response.statusCode() + ": " + response.body());
            }
            return parseEmbedding(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("embedding request failed", exception);
        }
    }

    private xin.v5ai.nb.infrastructure.model.ModelRuntimeConfig findEmbeddingModel() {
        return runtimeConfigGateway.findFirstEmbeddingModel();
    }

    private List<Float> parseEmbedding(String json) throws Exception {
        var root = OBJECT_MAPPER.readTree(json);
        var data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("embedding response has no data: " + json);
        }
        var values = new ArrayList<Float>();
        for (var value : data.get(0).path("embedding")) {
            values.add((float) value.asDouble());
        }
        return values;
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
