package xin.v5ai.nb.rag.core.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import java.net.URI;
import java.net.http.HttpRequest;

@Component
@RequiredArgsConstructor
public class MineruParseEngine implements DocumentParseEngine {
    private final ParserServiceProperties properties;
    @Override public String engine() { return "mineru"; }

    @Override
    public ParsedDocument parse(ParseRequest request) {
        var p = properties.getMineru();
        if (!p.isEnabled()) throw new IllegalStateException("MinerU 服务未启用");
        long started = System.currentTimeMillis();
        var client = ParserHttpSupport.client(p.getConnectTimeoutMillis());
        String base = p.getBaseUrl().replaceAll("/+$", "");
        var healthBuilder = HttpRequest.newBuilder(URI.create(base + "/health"))
                .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis())).GET();
        p.getHeaders().forEach(healthBuilder::header);
        ParserHttpSupport.requireSuccess(ParserHttpSupport.send(client, healthBuilder.build()));
        var fields = mineruFields(request);
        String boundary = "----v5ai-" + System.nanoTime();
        var submitBuilder = HttpRequest.newBuilder(URI.create(base + "/tasks"))
                .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(ParserHttpSupport.multipart(boundary, request.filename(), request.content(), fields)));
        p.getHeaders().forEach(submitBuilder::header);
        var submitted = ParserHttpSupport.send(client, submitBuilder.build());
        ParserHttpSupport.requireSuccess(submitted);
        String submitBody = ParserHttpSupport.text(submitted);
        Object submitJson = submitBody.isBlank() ? Map.of() : ParserHttpSupport.json(submitBody);
        String taskId = ParserHttpSupport.findTaskId(submitJson);
        if (taskId == null) throw new IllegalStateException("MinerU 未返回 task_id");
        long deadline = System.currentTimeMillis() + p.getPollTimeoutMillis();
        while (System.currentTimeMillis() < deadline) {
            var statusBuilder = HttpRequest.newBuilder(URI.create(base + "/tasks/" + taskId))
                    .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis())).GET();
            p.getHeaders().forEach(statusBuilder::header);
            var status = ParserHttpSupport.send(client, statusBuilder.build());
            ParserHttpSupport.requireSuccess(status);
            Object statusJson = ParserHttpSupport.json(ParserHttpSupport.text(status));
            if (ParserHttpSupport.isTerminal(statusJson)) break;
            sleep(p.getPollIntervalMillis());
        }
        var resultBuilder = HttpRequest.newBuilder(URI.create(base + "/tasks/" + taskId + "/result"))
                .timeout(java.time.Duration.ofMillis(p.getReadTimeoutMillis())).GET();
        p.getHeaders().forEach(resultBuilder::header);
        var result = ParserHttpSupport.send(client, resultBuilder.build());
        ParserHttpSupport.requireSuccess(result);
        String contentType = result.headers().firstValue("Content-Type").orElse("").toLowerCase();
        String raw;
        String text;
        if (contentType.contains("zip") || result.body().length >= 4 && result.body()[0] == 'P' && result.body()[1] == 'K') {
            text = ParserHttpSupport.zipText(result.body());
            String json = ParserHttpSupport.zipJson(result.body());
            raw = json == null || json.isBlank() ? "ZIP:" + result.body().length : json;
        } else {
            raw = ParserHttpSupport.text(result);
            Object json = contentType.contains("json") || raw.trim().startsWith("{") || raw.trim().startsWith("[") ? ParserHttpSupport.json(raw) : raw;
            text = json instanceof String s ? s : ParserHttpSupport.findText(json);
        }
        return ParsedDocument.builder().engine(engine()).text(text).markdown(text).structuredJson(raw)
                .diagnostics(new ParsedDocument.ParseDiagnostics("SUCCESS", System.currentTimeMillis() - started, java.util.List.of(), Map.of())).build();
    }

    private java.util.List<ParserHttpSupport.Part> mineruFields(ParseRequest request) {
        var result = new java.util.ArrayList<ParserHttpSupport.Part>();
        var m = request.params() == null ? null : request.params().getMineru();
        if (m == null) return result;
        if (m.getLangList() != null) m.getLangList().forEach(v -> put(result, "lang_list", v));
        put(result, "backend", m.getBackend()); put(result, "effort", m.getEffort()); put(result, "parse_method", m.getParseMethod());
        put(result, "server_url", m.getServerUrl()); put(result, "formula_enable", m.getFormulaEnable()); put(result, "table_enable", m.getTableEnable());
        put(result, "image_analysis", m.getImageAnalysis()); put(result, "return_md", m.getReturnMd()); put(result, "return_middle_json", m.getReturnMiddleJson());
        put(result, "return_model_output", m.getReturnModelOutput()); put(result, "return_content_list", m.getReturnContentList()); put(result, "return_images", m.getReturnImages());
        put(result, "response_format_zip", m.getResponseFormatZip()); put(result, "return_original_file", m.getReturnOriginalFile()); put(result, "client_side_output_generation", m.getClientSideOutputGeneration());
        put(result, "start_page_id", m.getStartPageId()); put(result, "end_page_id", m.getEndPageId());
        return result;
    }
    private void put(java.util.List<ParserHttpSupport.Part> fields, String key, Object value) { if (value != null) fields.add(new ParserHttpSupport.Part(key, String.valueOf(value))); }
    private void sleep(long millis) { try { Thread.sleep(Math.max(1, millis)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("MinerU 轮询被中断", e); } }
}
