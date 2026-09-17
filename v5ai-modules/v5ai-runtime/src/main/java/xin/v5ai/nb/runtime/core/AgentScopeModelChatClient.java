package xin.v5ai.nb.runtime.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;
import xin.v5ai.nb.common.agentscope.utils.ChatResponseTexts;

import java.time.Duration;
import java.util.List;

/**
 * {@link ModelChatClient} 的 AgentScope 实现（真 adapter）：
 * 模型解析经 {@link AgentModelResolver} 端口（配置→解密→构建模型的唯一实现处），
 * 本模块只负责发起对话、拼接文本与超时/错误映射。
 * 错误契约：配置缺失抛 {@link IllegalArgumentException}（由解析器抛出）；
 * 超时 / 连接失败 / 空响应抛 {@link ModelChatException}（消息含模型标签）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeModelChatClient implements ModelChatClient {

    private static final int MESSAGE_MAX_LENGTH = 200;

    private final AgentModelResolver modelResolver;

    @Override
    public String chatText(Long modelId, List<Msg> messages, Duration timeout) {
        ChatContext context = resolve(modelId);
        try {
            List<ChatResponse> responses = context.model().stream(
                            messages, List.of(), GenerateOptions.builder().stream(true).build())
                    .collectList()
                    .block(timeout);
            if (responses == null || responses.isEmpty()) {
                throw new ModelChatException("model chat returned no response: " + context.label());
            }
            String text = joinText(responses);
            if (text.isBlank()) {
                throw new ModelChatException("model chat returned empty content: " + context.label());
            }
            return text;
        } catch (ModelChatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelChatException(failureMessage(context, exception), exception);
        }
    }

    @Override
    public ChatResponse firstResponse(Long modelId, List<Msg> messages, GenerateOptions options, Duration timeout) {
        ChatContext context = resolve(modelId);
        try {
            ChatResponse first = context.model().stream(messages, List.of(), options).blockFirst(timeout);
            if (first == null) {
                throw new ModelChatException("model chat returned no response: " + context.label());
            }
            return first;
        } catch (ModelChatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelChatException(failureMessage(context, exception), exception);
        }
    }

    /**
     * 经模型解析端口取模型，并计算错误文案用的模型标签（解析细节不在此重复）。
     */
    private ChatContext resolve(Long modelId) {
        Model model = modelResolver.resolve(modelId);
        String label = model.getModelName() + " (modelId " + modelId + ")";
        return new ChatContext(model, label);
    }

    private static String failureMessage(ChatContext context, Exception exception) {
        String detail = sanitize(exception.getMessage(), exception.getClass().getSimpleName());
        if (detail.contains("Timeout on blocking read")) {
            return "model chat timed out contacting " + context.label();
        }
        return "model chat failed for " + context.label() + ": " + detail;
    }

    /** 拼接整段流的全部文本块（空响应/无文本块 → ""，语义由调用方判定）。 */
    private static String joinText(List<ChatResponse> responses) {
        StringBuilder text = new StringBuilder();
        for (ChatResponse response : responses) {
            String part = ChatResponseTexts.textOf(response);
            if (part != null) {
                text.append(part);
            }
        }
        return text.toString();
    }

    private static String sanitize(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() > MESSAGE_MAX_LENGTH
                ? cleaned.substring(0, MESSAGE_MAX_LENGTH) + "…"
                : cleaned;
    }

    private record ChatContext(Model model, String label) {
    }
}
