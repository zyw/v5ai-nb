package xin.v5ai.nb.common.agentscope.core;

import cn.hutool.json.JSONUtil;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.tools.WebSearchTool;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 联网搜索工具：响应格式化（纯逻辑）与缺参/缺 Key 的降级行为。
 * 真实 Tavily 调用不在此单测覆盖（依赖外部网络）。
 */
class WebSearchToolTest {

    @Test
    void formatResultsKeepsAnswerTitleUrlAndContent() {
        var response = JSONUtil.parseObj("""
                {"answer":"简明答案","results":[
                  {"title":"标题一","url":"https://a.example/1","content":"正文一"},
                  {"title":"标题二","url":"https://a.example/2","content":"正文二"}
                ]}""");

        String text = WebSearchTool.formatResults(response);

        assertThat(text).startsWith("简明答案");
        assertThat(text).contains("1. 标题一").contains("https://a.example/1").contains("正文一");
        assertThat(text).contains("2. 标题二").contains("https://a.example/2").contains("正文二");
    }

    @Test
    void formatResultsReportsNoResults() {
        String text = WebSearchTool.formatResults(JSONUtil.parseObj("{}"));

        assertThat(text).isEqualTo("no results found");
    }

    @Test
    void callErrorsWhenApiKeyMissing() {
        var param = ToolCallParam.builder().input(Map.of("query", "上海天气")).build();

        var block = new WebSearchTool(" ").callAsync(param).block();

        assertThat(block).isNotNull();
        assertThat(block.getState()).isEqualTo(ToolResultState.ERROR);
    }

    @Test
    void callErrorsWhenQueryMissing() {
        var param = ToolCallParam.builder().input(Map.of()).build();

        var block = new WebSearchTool("tvly-key").callAsync(param).block();

        assertThat(block).isNotNull();
        assertThat(block.getState()).isEqualTo(ToolResultState.ERROR);
    }
}