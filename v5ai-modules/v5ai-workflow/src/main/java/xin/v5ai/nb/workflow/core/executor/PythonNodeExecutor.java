package xin.v5ai.nb.workflow.core.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Calls a separately deployed isolated runner. Never executes submitted code in this JVM. */
@Component
public class PythonNodeExecutor implements WorkflowNodeExecutor {
    private final String runnerUrl;
    private final String runnerToken;

    public PythonNodeExecutor(@Value("${v5ai.workflow.python.runner-url:}") String runnerUrl,
                              @Value("${v5ai.workflow.python.runner-token:}") String runnerToken) {
        this.runnerUrl = runnerUrl == null ? "" : runnerUrl.trim();
        this.runnerToken = runnerToken == null ? "" : runnerToken;
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
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest healthRequest = HttpRequest.newBuilder(base.resolve("/health"))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + runnerToken).GET().build();
            HttpResponse<String> health = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            if (health.statusCode() != 200) throw new IllegalStateException("isolated Python Runner health check failed");

            HttpRequest execute = HttpRequest.newBuilder(base.resolve("/execute"))
                    .timeout(Duration.ofMillis(AgentNodeExecutor.positiveLong(node.config().get("timeoutMs"), 30_000L)))
                    .header("Authorization", "Bearer " + runnerToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(WorkflowJson.MAPPER.writeValueAsString(requestBody))).build();
            HttpResponse<String> response = client.send(execute, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("isolated Python Runner returned status " + response.statusCode());
            }
            Map<String, Object> result = WorkflowJson.MAPPER.readValue(response.body(),
                    new tools.jackson.core.type.TypeReference<>() {});
            Object outputs = result.get("outputs");
            if (!(outputs instanceof Map<?, ?> map)) throw new IllegalStateException("Python Runner response must contain an outputs object");
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            String outputVar = AgentNodeExecutor.text(node.config().get("outputVar"));
            if (outputVar != null && !outputVar.isBlank()) context.setVariable(outputVar, normalized);
            return NodeExecutionResult.of(normalized);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python Runner call was interrupted", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Python Runner call failed: " + e.getClass().getSimpleName());
        }
    }
}
