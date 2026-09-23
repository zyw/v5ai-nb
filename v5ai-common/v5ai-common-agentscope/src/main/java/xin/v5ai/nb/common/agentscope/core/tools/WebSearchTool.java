package xin.v5ai.nb.common.agentscope.core.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索工具：调用 Tavily Search API，把 Top 结果拼成文本返回给模型。
 * 由 {@code AgentDTO.webSearchEnabled} 控制是否注册；API Key 缺失/查询为空时返回友好错误而非抛异常。
 */
public class WebSearchTool extends ToolBase {

    public static final String TOOL_NAME = "web_search";
    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_RESULTS = 5;

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        super(ToolBase.builder()
                .name(TOOL_NAME)
                .description("Search the web for real-time information. Returns top results with title, URL and content.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "the search query")),
                        "required", List.of("query")))
                .readOnly(true));
        this.apiKey = apiKey;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.just(ToolResultBlock.error(
                    "web search is enabled but Tavily API key is not configured (v5ai.agentscope.tavily-api-key)"));
        }
        var input = param.getInput();
        var query = input == null ? null : input.get("query");
        String q = query == null ? "" : String.valueOf(query);
        if (q.isBlank()) {
            return Mono.just(ToolResultBlock.error("web_search requires a non-empty 'query' argument"));
        }
        return Mono.fromCallable(() -> ToolResultBlock.text(search(q)))
                .onErrorResume(e -> Mono.just(ToolResultBlock.error("web search failed: " + e.getMessage())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 执行 Tavily 检索并返回格式化结果文本。 */
    String search(String query) throws Exception {
        String body = JSONUtil.createObj()
                .set("api_key", apiKey)
                .set("query", query)
                .set("search_depth", "basic")
                .set("max_results", MAX_RESULTS)
                .toString();
        var request = HttpRequest.newBuilder(URI.create(TAVILY_URL))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Tavily HTTP " + response.statusCode());
        }
        return formatResults(JSONUtil.parseObj(response.body()));
    }

    /** 把 Tavily 响应拼成给模型的文本（包内可见，供单测直接喂响应 JSON）。 */
    public static String formatResults(JSONObject response) {
        var sb = new StringBuilder();
        var answer = response.getStr("answer");
        if (answer != null && !answer.isBlank()) {
            sb.append(answer).append("\n\n");
        }
        JSONArray results = response.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return sb.length() == 0 ? "no results found" : sb.toString().trim();
        }
        int index = 1;
        for (int i = 0; i < results.size(); i++) {
            JSONObject r = results.getJSONObject(i);
            sb.append(index++).append(". ").append(r.getStr("title")).append('\n')
                    .append(r.getStr("url")).append('\n')
                    .append(r.getStr("content")).append("\n\n");
        }
        return sb.toString().trim();
    }
}