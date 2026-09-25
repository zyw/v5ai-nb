package xin.v5ai.nb.workflow.core.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.TemplateResolver;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Outbound HTTP node. Targets must match the operator configured host allow-list. */
@Component
public class HttpNodeExecutor implements WorkflowNodeExecutor {
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final Set<String> FORBIDDEN_HEADERS = Set.of("host", "cookie", "proxy-authorization", "authorization");
    private final Set<String> allowedHosts;

    public HttpNodeExecutor(@Value("${v5ai.workflow.http.allowed-hosts:}") String allowedHosts) {
        this.allowedHosts = allowedHosts == null || allowedHosts.isBlank() ? Set.of()
                : Set.of(allowedHosts.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
    }

    @Override public WorkflowNodeType type() { return WorkflowNodeType.HTTP; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        if (allowedHosts.isEmpty()) throw new IllegalStateException("workflow HTTP executor is disabled: configure v5ai.workflow.http.allowed-hosts");
        String rawUrl = TemplateResolver.resolve(AgentNodeExecutor.text(node.config().get("url")), context);
        URI uri;
        try { uri = URI.create(rawUrl); }
        catch (RuntimeException e) { throw new IllegalArgumentException("HTTP node URL is invalid", e); }
        validateTarget(uri);

        String method = AgentNodeExecutor.text(node.config().get("method"));
        if (method == null) method = "GET";
        method = method.toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
            throw new IllegalArgumentException("HTTP node method is not allowed: " + method);
        }
        long timeout = AgentNodeExecutor.positiveLong(node.config().get("timeoutMs"), 10_000L);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(Math.min(timeout, 60_000L)));
        request.header("Accept", "application/json, text/plain;q=0.9");
        Object rawHeaders = node.config().get("headers");
        if (rawHeaders instanceof Map<?, ?> headers) {
            headers.forEach((key, value) -> {
                String header = String.valueOf(key);
                if (FORBIDDEN_HEADERS.contains(header.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("HTTP node sensitive header must use a platform secret reference: " + header);
                }
                request.header(header, TemplateResolver.resolve(String.valueOf(value), context));
            });
        }
        Object rawBody = node.config().get("body");
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (rawBody != null && !method.equals("GET")) {
            try {
                Object body = resolveTemplates(rawBody, context);
                publisher = HttpRequest.BodyPublishers.ofString(WorkflowJson.MAPPER.writeValueAsString(body));
                request.header("Content-Type", "application/json");
            } catch (Exception e) {
                throw new IllegalArgumentException("HTTP node request body cannot be serialized", e);
            }
        }
        request.method(method, publisher);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes = readLimited(response.body(), MAX_BODY_BYTES);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP node returned status " + response.statusCode());
            }
            Object body = text;
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (contentType.toLowerCase(Locale.ROOT).contains("json") && !text.isBlank()) {
                body = WorkflowJson.MAPPER.readValue(text, Object.class);
            }
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("status", response.statusCode());
            outputs.put("body", body);
            outputs.put("contentType", contentType);
            String outputVar = AgentNodeExecutor.text(node.config().get("outputVar"));
            if (outputVar != null && !outputVar.isBlank()) context.setVariable(outputVar, body);
            return NodeExecutionResult.of(outputs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP node was interrupted", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("HTTP node request failed: " + e.getClass().getSimpleName());
        }
    }

    private void validateTarget(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("HTTP node URL must be an http(s) URL without user info");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) throw new IllegalArgumentException("HTTP node host is not allow-listed: " + host);
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("HTTP node host resolves to a non-public address");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("HTTP node host cannot be resolved", e);
        }
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) throw new IllegalStateException("HTTP node response exceeds 1 MiB");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private Object resolveTemplates(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text) return TemplateResolver.resolve(text, context);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, child) -> resolved.put(String.valueOf(key), resolveTemplates(child, context)));
            return resolved;
        }
        if (value instanceof Iterable<?> values) {
            java.util.List<Object> resolved = new java.util.ArrayList<>();
            values.forEach(child -> resolved.add(resolveTemplates(child, context)));
            return resolved;
        }
        return value;
    }
}
