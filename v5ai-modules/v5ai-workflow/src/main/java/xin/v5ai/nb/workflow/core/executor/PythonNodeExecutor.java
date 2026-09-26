package xin.v5ai.nb.workflow.core.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Calls a separately deployed isolated runner. Never executes submitted code in this JVM. */
@Component
public class PythonNodeExecutor implements WorkflowNodeExecutor {
    private static final long RUNNER_MAX_TIMEOUT_MILLIS = 30_000L;
    private final String runnerUrl;
    private final String runnerToken;
    private final long requestTimeoutMillis;
    private final long readinessCacheMillis;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final HttpClient client;
    private final AtomicLong readyUntilNanos = new AtomicLong();

    public PythonNodeExecutor(String runnerUrl, String runnerToken) {
        this(runnerUrl, runnerToken, 35_000L, 10L, 1_048_576, 1_048_576);
    }

    public PythonNodeExecutor(String runnerUrl, String runnerToken, long requestTimeoutMillis,
                              long readinessCacheSeconds, int maxResponseBytes) {
        this(runnerUrl, runnerToken, requestTimeoutMillis, readinessCacheSeconds, 1_048_576, maxResponseBytes);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PythonNodeExecutor(@Value("${v5ai.workflow.python.runner-url:}") String runnerUrl,
                              @Value("${v5ai.workflow.python.runner-token:}") String runnerToken,
                              @Value("${v5ai.workflow.python.request-timeout-millis:35000}") long requestTimeoutMillis,
                              @Value("${v5ai.workflow.python.readiness-cache-seconds:10}") long readinessCacheSeconds,
                              @Value("${v5ai.workflow.python.max-request-bytes:1048576}") int maxRequestBytes,
                              @Value("${v5ai.workflow.python.max-response-bytes:1048576}") int maxResponseBytes) {
        this.runnerUrl = runnerUrl == null ? "" : runnerUrl.trim();
        this.runnerToken = runnerToken == null ? "" : runnerToken;
        this.requestTimeoutMillis = Math.max(1_000L, Math.min(requestTimeoutMillis, 360_000L));
        this.readinessCacheMillis = Math.max(0L, Math.min(readinessCacheSeconds, 300L)) * 1_000L;
        this.maxRequestBytes = Math.max(128, Math.min(maxRequestBytes, 8 * 1024 * 1024));
        this.maxResponseBytes = Math.max(128, Math.min(maxResponseBytes, 8 * 1024 * 1024));
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public WorkflowNodeType type() { return WorkflowNodeType.PYTHON; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        if (runnerUrl.isBlank() || runnerToken.isBlank()) {
            throw new IllegalStateException("Python execution is unavailable: configure an isolated workflow Python Runner");
        }
        URI base = URI.create(runnerUrl);
        if (!"https".equalsIgnoreCase(base.getScheme()) && !"http".equalsIgnoreCase(base.getScheme())) {
            throw new IllegalStateException("Python Runner URL must use HTTP or HTTPS");
        }
        String script = AgentNodeExecutor.text(node.config().get("script"));
        if (script == null) script = AgentNodeExecutor.text(node.config().get("code"));
        if (script == null || script.isBlank()) throw new IllegalArgumentException("Python node script is required");
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("runId", context.runId());
        requestBody.put("nodeId", node.id());
        requestBody.put("script", script);
        requestBody.put("inputs", context.snapshot());
        requestBody.put("timeoutMs", Math.min(AgentNodeExecutor.positiveLong(node.config().get("timeoutMs"),
                RUNNER_MAX_TIMEOUT_MILLIS), RUNNER_MAX_TIMEOUT_MILLIS));
        try {
            String requestJson = WorkflowJson.MAPPER.writeValueAsString(requestBody);
            if (requestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxRequestBytes) {
                throw new IllegalStateException("Python Runner request exceeded configured size limit");
            }
            ensureReady(base);
            HttpRequest execute = HttpRequest.newBuilder(base.resolve("/execute"))
                    .timeout(Duration.ofMillis(requestTimeoutMillis))
                    .header("Authorization", "Bearer " + runnerToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson)).build();
            HttpResponse<InputStream> response = client.send(execute, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(maxResponseBytes + 1);
            }
            if (body.length > maxResponseBytes) throw new IllegalStateException("Python Runner response exceeded configured size limit");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw runnerFailure(response.statusCode(), body);
            }
            Map<String, Object> result = WorkflowJson.MAPPER.readValue(body,
                    new tools.jackson.core.type.TypeReference<>() {});
            Object outputs = result.get("outputs");
            if (!(outputs instanceof Map<?, ?> map)) throw new IllegalStateException("Python Runner response must contain an outputs object");
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (!(key instanceof String)) throw new IllegalStateException("Python Runner output keys must be strings");
                normalized.put((String) key, value);
            });
            String outputVar = AgentNodeExecutor.text(node.config().get("outputVar"));
            if (outputVar != null && !outputVar.isBlank()) context.setVariable(outputVar, normalized);
            return NodeExecutionResult.of(normalized);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python Runner call was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Python Runner call failed: " + e.getClass().getSimpleName());
        }
    }

    private void ensureReady(URI base) throws IOException, InterruptedException {
        long now = System.nanoTime();
        if (readinessCacheMillis > 0 && readyUntilNanos.get() > now) return;
        HttpRequest healthRequest = HttpRequest.newBuilder(base.resolve("/health"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + runnerToken).GET().build();
        HttpResponse<Void> health = client.send(healthRequest, HttpResponse.BodyHandlers.discarding());
        if (health.statusCode() != 200) {
            readyUntilNanos.set(0);
            throw new IllegalStateException("isolated Python Runner health check failed (HTTP " + health.statusCode() + ")");
        }
        if (readinessCacheMillis > 0) readyUntilNanos.set(System.nanoTime() + Duration.ofMillis(readinessCacheMillis).toNanos());
    }

    private static IllegalStateException runnerFailure(int status, byte[] body) {
        String code = "UNKNOWN";
        try {
            Map<String, Object> error = WorkflowJson.MAPPER.readValue(body,
                    new tools.jackson.core.type.TypeReference<>() {});
            if (error.get("error") instanceof Map<?, ?> detail && detail.get("code") instanceof String value
                    && value.matches("[A-Z][A-Z0-9_]{0,63}")) code = value;
        } catch (Exception ignored) {
            // Deliberately do not forward response bodies or internal diagnostics.
        }
        return new IllegalStateException("isolated Python Runner failed (HTTP " + status + ", code " + code + ")");
    }
}
