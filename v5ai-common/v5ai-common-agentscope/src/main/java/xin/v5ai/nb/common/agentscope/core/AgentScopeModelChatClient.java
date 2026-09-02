package xin.v5ai.nb.common.agentscope.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;
import xin.v5ai.nb.common.encrypt.utils.CipherUtils;

import java.time.Duration;
import java.util.List;

/**
 * {@link ModelChatClient} 的 AgentScope 实现（真 adapter）：
 * 按 modelId 解析运行时配置、解密密文凭据、经 {@link AgentScopeModelFactory} 构建模型后发起对话。
 * 错误契约：配置缺失抛 {@link IllegalArgumentException}；超时 / 连接失败 / 空响应抛
 * {@link ModelChatException}（消息含目标模型与端点标签）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeModelChatClient implements ModelChatClient {

    private static final int MESSAGE_MAX_LENGTH = 200;

    private final ModelRuntimeConfigGateway runtimeConfigGateway;
    private final AgentScopeModelFactory modelFactory;

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
     * 解析运行时配置 + 解密凭据 + 构建模型；同时计算错误文案用的模型标签。
     */
    private ChatContext resolve(Long modelId) {
        ModelRuntimeConfigDTO config = runtimeConfigGateway.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("model runtime config not found: " + modelId);
        }
        String plaintext = CipherUtils.decrypt(config.credentialsCiphertext());
        Model model = modelFactory.create(config, plaintext);
        String label = describeTarget(config, plaintext);
        return new ChatContext(model, label);
    }

    private static String describeTarget(ModelRuntimeConfigDTO config, String plaintextCredentials) {
        String baseUrl = plaintextCredentials == null
                ? null : AgentModelCredentialConfig.parse(plaintextCredentials).baseUrl();
        String target = baseUrl == null || baseUrl.isBlank() ? "(default endpoint)" : baseUrl;
        return target + " (" + config.providerType() + "/" + config.modelKey()
                + ", modelId " + config.modelId() + ")";
    }

    private static String failureMessage(ChatContext context, Exception exception) {
        String detail = sanitize(exception.getMessage(), exception.getClass().getSimpleName());
        if (detail.contains("Timeout on blocking read")) {
            return "model chat timed out contacting " + context.label();
        }
        return "model chat failed for " + context.label() + ": " + detail;
    }

    /** 拼接全部响应的 TextBlock 文本。 */
    private static String joinText(List<ChatResponse> responses) {
        StringBuilder text = new StringBuilder();
        for (ChatResponse response : responses) {
            if (response.getContent() == null) {
                continue;
            }
            for (var block : response.getContent()) {
                if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                    text.append(textBlock.getText());
                }
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
