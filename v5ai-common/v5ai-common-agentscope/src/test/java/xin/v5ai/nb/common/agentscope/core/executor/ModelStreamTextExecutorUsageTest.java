package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 直连路径的用量事件：流开头一条估算（{@code estimated=true}），流末尾一条模型回报的真实值
 * （{@code estimated=false}）；服务端不回报时只有估算那一条。
 */
class ModelStreamTextExecutorUsageTest {

    private static AgentDTO agent() {
        return new AgentDTO("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null, false, false, false, false, false, RagCallMode.FORCED.value(),
                null, true);
    }

    private static ChatResponse response(String text, int inputTokens, int outputTokens) {
        return ChatResponse.builder()
                .content(List.of(TextBlock.builder().text(text).build()))
                .usage(ChatUsage.builder().inputTokens(inputTokens).outputTokens(outputTokens).build())
                .build();
    }

    private static Model model(Flux<ChatResponse> responses) {
        var model = mock(Model.class);
        when(model.getModelName()).thenReturn("gpt-4o");
        when(model.stream(anyList(), anyList(), any(GenerateOptions.class))).thenReturn(responses);
        return model;
    }

    private static List<AgentTextEvent.Usage> usagesOf(List<AgentTextEvent> events) {
        return events.stream().filter(AgentTextEvent.Usage.class::isInstance)
                .map(AgentTextEvent.Usage.class::cast).toList();
    }

    @Test
    void directPathReportsEstimatedThenRealUsage() {
        var executor = new ModelStreamTextExecutor(model(Flux.just(
                response("你", 5, 1), response("好", 10, 2))));

        var events = executor.streamText(agent(), new AgentRunBo("agent-x", "c", "问题")).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.get(0)).isInstanceOf(AgentTextEvent.ModelCall.class);
        var usages = usagesOf(events);
        assertThat(usages).hasSize(2);
        assertThat(usages.get(0).estimated()).isTrue();
        assertThat(usages.get(0).inputTokens()).isPositive();
        assertThat(usages.get(1).estimated()).isFalse();
        // 服务端每块都带用量时是累计值：取最后一次，不能相加
        assertThat(usages.get(1).inputTokens()).isEqualTo(10L);
        assertThat(usages.get(1).outputTokens()).isEqualTo(2L);
        assertThat(events.stream().filter(AgentTextEvent.Text.class::isInstance)).hasSize(2);
    }

    /** 会话摘要进的是系统提示（与 RAG 上下文同一处、两条路径共用）。 */
    @Test
    void summaryContextIsInjectedIntoTheSystemPrompt() {
        var model = mock(Model.class);
        when(model.getModelName()).thenReturn("gpt-4o");
        when(model.stream(anyList(), anyList(), any(GenerateOptions.class))).thenReturn(Flux.empty());
        var executor = new ModelStreamTextExecutor(model);

        executor.streamText(agent(), new AgentRunBo("agent-x", "c", "问题").withSummaryContext("更早：用户在做 A"))
                .collectList().block();

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(model).stream(captor.capture(), anyList(), any(GenerateOptions.class));
        List<?> captured = captor.getValue();
        var system = (io.agentscope.core.message.Msg) captured.get(0);
        assertThat(system.getTextContent()).contains("更早：用户在做 A");
    }

    @Test
    void missingUsageLeavesOnlyTheEstimate() {
        var executor = new ModelStreamTextExecutor(model(Flux.just(
                ChatResponse.builder().content(List.of(TextBlock.builder().text("hi").build())).build())));

        var events = executor.streamText(agent(), new AgentRunBo("agent-x", "c", "问题")).collectList().block();

        assertThat(usagesOf(events)).singleElement()
                .satisfies(usage -> assertThat(usage.estimated()).isTrue());
    }
}
