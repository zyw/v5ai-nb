package xin.v5ai.nb.rag.core.parser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalParseEngineTest {
    private HttpServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void doclingUsesAsyncContractAndNormalizesMarkdown() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", e -> respond(e, 200, "{}", "application/json"));
        server.createContext("/v1/convert/file/async", e -> respond(e, 200, "{\"task_id\":\"d1\"}", "application/json"));
        server.createContext("/v1/status/poll/d1", e -> respond(e, 200, "{\"status\":\"success\"}", "application/json"));
        server.createContext("/v1/result/d1", e -> respond(e, 200, "{\"document\":{\"md_content\":\"# docling\"}}", "application/json"));
        server.start();
        var props = new ParserServiceProperties();
        props.getDocling().setEnabled(true);
        props.getDocling().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getDocling().setPollIntervalMillis(1);

        var result = new DoclingParseEngine(props).parse(request("docling", null));

        assertThat(result.content()).isEqualTo("# docling");
        assertThat(result.getStructuredJson()).contains("md_content");
    }

    @Test
    void mineruUsesTasksContractAndMapsFields() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> multipart = new AtomicReference<>();
        server.createContext("/health", e -> respond(e, 200, "{}", "application/json"));
        server.createContext("/tasks", e -> { multipart.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); respond(e, 202, "{\"task_id\":\"m1\"}", "application/json"); });
        server.createContext("/tasks/m1", e -> respond(e, 200, "{\"state\":\"completed\"}", "application/json"));
        server.createContext("/tasks/m1/result", e -> respond(e, 200, "{\"markdown\":\"# mineru\",\"unknown\":42}", "application/json"));
        server.start();
        var props = new ParserServiceProperties();
        props.getMineru().setEnabled(true);
        props.getMineru().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getMineru().setPollIntervalMillis(1);
        var params = xin.v5ai.nb.rag.core.config.RagConfigDO.ParseParams.builder().engine("mineru")
                .mineru(xin.v5ai.nb.rag.core.config.RagConfigDO.MineruParams.builder().backend("hybrid-engine").returnMd(true).build()).build();

        var result = new MineruParseEngine(props).parse(new ParseRequest("pdf".getBytes(), "a.pdf",
                xin.v5ai.nb.rag.core.enums.DocumentFileType.PDF, 1L, 2L, params));

        assertThat(result.content()).isEqualTo("# mineru");
        assertThat(result.getStructuredJson()).contains("unknown");
        assertThat(multipart).hasValueSatisfying(body -> assertThat(body).contains("name=\"backend\"", "hybrid-engine", "name=\"return_md\""));
    }

    @Test
    void mineruReadsMarkdownAndJsonFromZipResult() throws Exception {
        byte[] zip = zip(Map.of("result.md", "# zipped", "middle.json", "{\"page\":1}"));
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", e -> respond(e, 200, "{}", "application/json"));
        server.createContext("/tasks", e -> respond(e, 202, "{\"task_id\":\"z1\"}", "application/json"));
        server.createContext("/tasks/z1", e -> respond(e, 200, "{\"status\":\"completed\"}", "application/json"));
        server.createContext("/tasks/z1/result", e -> respond(e, 200, zip, "application/zip"));
        server.start();
        var props = new ParserServiceProperties();
        props.getMineru().setEnabled(true);
        props.getMineru().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getMineru().setPollIntervalMillis(1);

        var result = new MineruParseEngine(props).parse(request("mineru", null));

        assertThat(result.content()).isEqualTo("# zipped");
        assertThat(result.getStructuredJson()).contains("page");
    }

    private ParseRequest request(String engine, xin.v5ai.nb.rag.core.config.RagConfigDO.MineruParams mineru) {
        var params = xin.v5ai.nb.rag.core.config.RagConfigDO.ParseParams.builder().engine(engine).mineru(mineru).build();
        return new ParseRequest("pdf".getBytes(), "a.pdf", xin.v5ai.nb.rag.core.enums.DocumentFileType.PDF, 1L, 2L, params);
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void respond(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static byte[] zip(java.util.Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
