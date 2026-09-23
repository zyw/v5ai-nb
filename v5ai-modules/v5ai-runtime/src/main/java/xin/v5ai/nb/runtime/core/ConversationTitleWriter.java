package xin.v5ai.nb.runtime.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.runtime.core.config.properties.ConversationTitleProperties;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.core.utils.AuxiliaryModel;
import xin.v5ai.nb.runtime.core.utils.ConversationNaming;

import java.time.Duration;
import java.util.List;

/**
 * 会话标题的实际生成者：首轮结束后异步调一次小模型，把兜底名换成一个短标题。
 *
 * <p>几个刻意的取舍：</p>
 * <ul>
 *   <li><b>异步但不新增线程池</b>：跑在 Reactor 的 {@code boundedElastic} 上（本仓库里联网搜索、
 *       MCP 工具等阻塞调用的既有做法）。{@code @Async} 在没有自定义 {@code TaskExecutor} 时
 *       用的是「一次调用一个线程」的 {@code SimpleAsyncTaskExecutor}，新会话突发时不受控。</li>
 *   <li><b>失败静默</b>：模型超时/限流/输出为空都只记 debug 日志——兜底名已经在库里。</li>
 *   <li><b>不写用量账</b>：这是平台内部行为，不该污染某个 Agent 的用量口径。</li>
 *   <li><b>条件回写</b>：经 {@code renameIfAutoNamed}（{@code WHERE name_source = 'AUTO'}），
 *       用户在这期间改了名就改不动，天然没有竞态。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTitleWriter implements ConversationTitleGenerator {

    private final PublishedAgentResolver publishedAgentResolver;
    private final ModelChatClient modelChatClient;
    private final ConversationService conversationService;
    private final ConversationTitleProperties properties;

    @Override
    public void rewriteIfAutoNamed(String conversationId, String agentKey, String question) {
        if (!properties.isEnabled()) {
            return;
        }
        var prompt = ConversationNaming.questionForTitle(question);
        if (prompt == null) {
            // 纯图片提问之类没有文字内容：没有可命名的素材，保留兜底名
            return;
        }
        Mono.fromRunnable(() -> rewrite(conversationId, agentKey, prompt))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {
                }, error -> log.debug("会话 {} 的标题改写失败：{}", conversationId, error.getMessage()));
    }

    /**
     * 真正的一次标题生成：解析次要模型（未配置则回退绑定的对话模型）→ 一次最小对话 →
     * 清洗 → 条件回写。任何异常都在这里被吞掉（调用方是 fire-and-forget 的异步任务，
     * 无人接住异常）。
     */
    private void rewrite(String conversationId, String agentKey, String prompt) {
        try {
            var agent = publishedAgentResolver.resolve(agentKey);
            var modelId = AuxiliaryModel.resolve(agent);
            if (modelId == null) {
                // 未绑定模型：没有可调用的模型，保留兜底名（与摘要侧的同类保护对齐）
                log.debug("会话 {} 的标题跳过：Agent {} 未解析出可用模型", conversationId, agentKey);
                return;
            }
            // 这条链路不记用量、不落事件，「用的哪个模型」只能靠日志归因
            log.debug("会话 {} 的标题改使用{}模型 modelId={}",
                    conversationId, AuxiliaryModel.isSecondary(agent) ? "次要" : "主", modelId);
            var messages = List.of(
                    Msg.builder().role(MsgRole.SYSTEM).textContent(systemPrompt()).build(),
                    Msg.builder().role(MsgRole.USER).textContent(prompt).build());
            var raw = modelChatClient.chatText(modelId, messages,
                    Duration.ofMillis(properties.getTimeoutMillis()));
            var title = ConversationNaming.cleanModelTitle(raw, properties.getMaxLength());
            if (title != null) {
                conversationService.renameIfAutoNamed(conversationId, title);
            }
        } catch (Exception exception) {
            log.debug("会话 {} 的标题生成失败（保留兜底名）：{}", conversationId, exception.getMessage());
        }
    }

    private String systemPrompt() {
        return """
                你是会话命名助手。根据用户的第一句提问，生成一个简洁的会话标题，不超过 %d 个字。
                只输出标题本身：不要引号、不要句末标点、不要「标题：」之类的前缀、不要解释、不要换行。
                使用与用户提问相同的语言。
                """.formatted(properties.getMaxLength()).trim();
    }
}
