package xin.v5ai.nb.workflow.core.executor;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PythonNodeExecutorTest {
    private HttpServer server;
    private final AtomicInteger healthCalls = new AtomicInteger();
    private int executeStatus = 200;
    private String executeBody = "{\"outputs\":{\"answer\":42}}";
    private String executeRequest = "";

    private String startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            healthCalls.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/execute", exchange -> {
            executeRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = executeBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(executeStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test void acceptsLegacyCodeAndSkipsEmptyOutputVariable() throws Exception {
        String url = startServer();
        PythonNodeExecutor executor = new PythonNodeExecutor(url, "long-test-token-012345678901234567", 5000, 30, 1024);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("code", "result = inputs");
        config.put("outputVar", " ");
        WorkflowExecutionContext context = new WorkflowExecutionContext("run-1", "flow", 1L, Map.of("x", 21), Map.of());

        var result = executor.execute(new WorkflowNode("python-node", WorkflowNodeType.PYTHON, "Python", config), context);

        assertEquals(Map.of("answer", 42), result.outputs());
        assertFalse(context.variables().containsKey(""));
        assertTrue(executeRequest.contains("\"script\":\"result = inputs\""));
        assertTrue(executeRequest.contains("\"timeoutMs\":30000"));
        assertEquals(1, healthCalls.get());
    }

    @Test void reportsStableRunnerErrorCodeWithoutForwardingResponseMessage() throws Exception {
        String url = startServer();
        executeStatus = 504;
        executeBody = "{\"error\":{\"code\":\"EXECUTION_TIMEOUT\",\"message\":\"/private/path secret\"}}";
        PythonNodeExecutor executor = new PythonNodeExecutor(url, "long-test-token-012345678901234567", 5000, 0, 1024);
        WorkflowNode node = new WorkflowNode("py", WorkflowNodeType.PYTHON, "Python", Map.of("script", "result = {}"));
        WorkflowExecutionContext context = new WorkflowExecutionContext("run", "flow", 1L, Map.of(), Map.of());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> executor.execute(node, context));
        assertTrue(error.getMessage().contains("EXECUTION_TIMEOUT"));
        assertFalse(error.getMessage().contains("/private/path"));
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test void rejectsOversizedResponseBody() throws Exception {
        String url = startServer();
        executeBody = "x".repeat(200);
        PythonNodeExecutor executor = new PythonNodeExecutor(url, "long-test-token-012345678901234567", 5000, 0, 128);
        WorkflowNode node = new WorkflowNode("py", WorkflowNodeType.PYTHON, "Python", Map.of("script", "result = {}"));
        WorkflowExecutionContext context = new WorkflowExecutionContext("run", "flow", 1L, Map.of(), Map.of());

        assertTrue(assertThrows(IllegalStateException.class, () -> executor.execute(node, context))
                .getMessage().contains("size limit"));
    }

    @Test void rejectsOversizedRequestBeforeCallingRunner() throws Exception {
        String url = startServer();
        PythonNodeExecutor executor = new PythonNodeExecutor(url, "long-test-token-012345678901234567", 5000, 0, 128, 1024);
        WorkflowNode node = new WorkflowNode("py", WorkflowNodeType.PYTHON, "Python", Map.of("script", "result = {}"));
        WorkflowExecutionContext context = new WorkflowExecutionContext("run", "flow", 1L, Map.of("large", "x".repeat(512)), Map.of());

        assertTrue(assertThrows(IllegalStateException.class, () -> executor.execute(node, context))
                .getMessage().contains("request exceeded"));
        assertEquals(0, executeRequest.length());
    }

    @Test void configurationMissingFailsClosedWithoutLocalFallback() {
        PythonNodeExecutor executor = new PythonNodeExecutor("", "", 35_000, 10, 1024);
        WorkflowNode node = new WorkflowNode("py", WorkflowNodeType.PYTHON, "Python", Map.of("script", "result = {}"));
        WorkflowExecutionContext context = new WorkflowExecutionContext("run", "flow", 1L, Map.of(), Map.of());
        assertTrue(assertThrows(IllegalStateException.class, () -> executor.execute(node, context)).getMessage().contains("unavailable"));
    }
}
