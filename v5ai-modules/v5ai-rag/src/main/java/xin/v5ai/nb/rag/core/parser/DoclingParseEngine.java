package xin.v5ai.nb.rag.core.parser;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Component
@RequiredArgsConstructor
public class DoclingParseEngine implements DocumentParseEngine {
    private final ParserServiceProperties properties;
    private volatile Semaphore concurrencyLimiter;

    @Override public String engine() { return "docling"; }

    @Override
    public ParsedDocument parse(ParseRequest request) {
        var limiter = limiter();
        boolean acquired = false;
        try {
            limiter.acquire();
            acquired = true;
            return parseInternal(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docling 并发等待被中断", e);
        } finally {
            if (acquired) limiter.release();
        }
    }

    private ParsedDocument parseInternal(ParseRequest request) {
        var p = properties.getDocling();
        if (!p.isEnabled()) throw new IllegalStateException("Docling 服务未启用");
        long started = System.currentTimeMillis();
        var client = ParserHttpSupport.client(p.getConnectTimeoutMillis());
        String base = p.getBaseUrl().replaceAll("/+$", "");
        var healthBuilder = HttpRequest.newBuilder(URI.create(base + "/health"))
                .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis())).GET();
        if (p.getApiKey() != null && !p.getApiKey().isBlank()) healthBuilder.header("X-Api-Key", p.getApiKey());
        var health = client.sendAsync(healthBuilder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray()).join();
        ParserHttpSupport.requireSuccess(health);
        List<ParserHttpSupport.Part> fields = new ArrayList<>();
        fields.add(new ParserHttpSupport.Part("to_formats", "md"));
        fields.add(new ParserHttpSupport.Part("to_formats", "json"));
        var d = request.params() == null ? null : request.params().getDocling();
        if (d != null) {
            if (d.getDoOcr() != null) fields.add(new ParserHttpSupport.Part("do_ocr", String.valueOf(d.getDoOcr())));
            if (d.getDoTableStructure() != null) fields.add(new ParserHttpSupport.Part("do_table_structure", String.valueOf(d.getDoTableStructure())));
            if (d.getImageExportMode() != null) fields.add(new ParserHttpSupport.Part("image_export_mode", d.getImageExportMode()));
            if (d.getPdfBackend() != null) fields.add(new ParserHttpSupport.Part("pdf_backend", d.getPdfBackend()));
            if (d.getOcrLang() != null) d.getOcrLang().forEach(v -> fields.add(new ParserHttpSupport.Part("ocr_lang", v)));
        }
        String boundary = "----v5ai-" + System.nanoTime();
        var body = ParserHttpSupport.multipart(boundary, request.filename(), request.content(), fields);
        var builder = HttpRequest.newBuilder(URI.create(base + "/v1/convert/file/async"))
                .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (p.getApiKey() != null && !p.getApiKey().isBlank()) builder.header("X-Api-Key", p.getApiKey());
        var submitted = ParserHttpSupport.send(client, builder.build());
        ParserHttpSupport.requireSuccess(submitted);
        Object submittedJson = ParserHttpSupport.json(ParserHttpSupport.text(submitted));
        String taskId = ParserHttpSupport.findTaskId(submittedJson);
        if (taskId == null) throw new IllegalStateException("Docling 未返回 task_id");
        long deadline = System.currentTimeMillis() + p.getPollTimeoutMillis();
        while (System.currentTimeMillis() < deadline) {
            var status = ParserHttpSupport.send(client, HttpRequest.newBuilder(URI.create(base + "/v1/status/poll/" + taskId))
                    .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis())).GET().build());
            ParserHttpSupport.requireSuccess(status);
            Object statusJson = ParserHttpSupport.json(ParserHttpSupport.text(status));
            if (ParserHttpSupport.isTerminal(statusJson)) break;
            sleep(p.getPollIntervalMillis());
        }
        var result = ParserHttpSupport.send(client, HttpRequest.newBuilder(URI.create(base + "/v1/result/" + taskId))
                .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis())).GET().build());
        ParserHttpSupport.requireSuccess(result);
        String raw = ParserHttpSupport.text(result);
        Object parsed = JSONUtil.isTypeJSON(raw) ? ParserHttpSupport.json(raw) : raw;
        String text = parsed instanceof String s ? s : ParserHttpSupport.findText(parsed);
        return ParsedDocument.builder().engine(engine()).text(text).markdown(text).structuredJson(raw)
                .diagnostics(new ParsedDocument.ParseDiagnostics("SUCCESS", System.currentTimeMillis() - started, java.util.List.of(), Map.of())).build();
    }

    private Semaphore limiter() {
        var current = concurrencyLimiter;
        if (current == null) {
            synchronized (this) {
                current = concurrencyLimiter;
                if (current == null) {
                    current = new Semaphore(Math.max(1, properties.getDocling().getConcurrency()));
                    concurrencyLimiter = current;
                }
            }
        }
        return current;
    }

    private void sleep(long millis) {
        try { Thread.sleep(Math.max(1, millis)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Docling 轮询被中断", e); }
    }
}
