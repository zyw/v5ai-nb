package xin.v5ai.nb.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;

import java.util.ArrayList;
import java.util.List;

/**
 * 直接基于 AgentScope {@link Model} 流式输出的文本执行器。
 *
 * 能力：
 * - 支持多轮对话历史回放（request.history 拼进消息列表）；
 * - 支持注入 RAG 上下文（放进系统提示）；
 * - 发起模型请求前先发 {@code MODEL_CALL} 标记，供运行时转为平台事件。
 *
 * 相比 AgentScopeHarnessExecutor，它不经过 HarnessAgent 封装，
 * 直接调用模型流式接口，更轻量。
 */
public class ModelStreamTextExecutor implements AgentTextExecutor {
    private final Model model;

    public ModelStreamTextExecutor(Model model) {
        this.model = model;
    }

    @Override
    public Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request) {
        // 组装完整消息列表（系统提示 + 历史 + 当前提问）
        List<Msg> messages = buildMessages(application, request);
        return Flux.concat(
                // 先发"模型调用开始"标记
                Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                // 流式调用模型，提取文本增量，过滤空片段，转成 Text 事件
                model.stream(messages, List.of(), generateOptions())
                        .map(ModelStreamTextExecutor::extractText)
                        .filter(text -> text != null && !text.isEmpty())
                        .map(AgentTextEvent.Text::new));
    }

    /**
     * 组装发送给模型的完整消息列表：
     * 1) SYSTEM 提示：应用身份 + 描述 + 可选的 RAG 知识上下文；
     * 2) 历史消息：按角色映射为 USER/ASSISTANT；
     * 3) 当前用户提问。
     */
    private static List<Msg> buildMessages(Application application, AgentRunRequest request) {
        List<Msg> messages = new ArrayList<>();
        var system = new StringBuilder();
        system.append("You are the assistant for application \"").append(application.name()).append("\".");
        if (application.description() != null && !application.description().isBlank()) {
            system.append(' ').append(application.description());
        }
        if (request.ragContext() != null && !request.ragContext().isBlank()) {
            system.append("\n\nUse the following knowledge base context when relevant:\n")
                    .append(request.ragContext());
        }
        messages.add(Msg.builder().role(MsgRole.SYSTEM).textContent(system.toString()).build());

        // 回放历史消息（USER/ASSISTANT 角色映射）
        for (Message history : request.history()) {
            var role = history.role() == MessageRole.USER ? MsgRole.USER : MsgRole.ASSISTANT;
            messages.add(Msg.builder().role(role).textContent(history.content()).build());
        }
        // 追加当前提问
        messages.add(Msg.builder().role(MsgRole.USER).textContent(request.query()).build());
        return messages;
    }

    /**
     * 生成选项：开启流式输出。
     */
    private static GenerateOptions generateOptions() {
        return GenerateOptions.builder().stream(true).build();
    }

    /**
     * 从模型响应中提取文本内容：
     * 拼接所有 TextBlock 的文本；响应为空或无内容时返回 null。
     */
    private static String extractText(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }
        var text = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}
