package xin.v5ai.nb.runtime.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.runtime.core.config.properties.ConversationSummaryProperties;
import xin.v5ai.nb.runtime.core.config.properties.HistoryWindowProperties;
import xin.v5ai.nb.runtime.core.domain.ConversationSummaryDTO;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.core.utils.AuxiliaryModel;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话摘要的实际生成者：把「历史窗口之外」的旧消息滚动压缩成一段摘要。
 *
 * <p>流程与 {@link ConversationTitleWriter} 完全同构（这也是它可预期的原因）：回答落库之后
 * 异步触发 → 判定是否值得压 → 一次小模型调用 → 条件回写 → 失败静默。</p>
 *
 * <p>三个关键口径：</p>
 * <ul>
 *   <li><b>边界</b>：摘要只覆盖「历史窗口最早那条消息之前」的内容，窗口内的消息永远逐条回放；</li>
 *   <li><b>水位</b>：写入带 {@code covered_until_message_id}，只有前进才覆盖（并发安全）；</li>
 *   <li><b>不记用量</b>：这是平台内部行为，不污染 Agent 的用量与配额（与标题改写一致）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummaryWriter implements ConversationSummaryGenerator {

    private final PublishedAgentResolver publishedAgentResolver;
    private final ModelChatClient modelChatClient;
    private final MessageService messageService;
    private final ConversationSummaryService summaryService;
    private final ConversationSummaryProperties properties;
    private final HistoryWindowProperties windowProperties;

    @Override
    public void summarizeIfNeeded(String conversationId, String agentKey) {
        if (!properties.isEnabled() || conversationId == null || conversationId.isBlank()) {
            return;
        }
        Mono.fromRunnable(() -> summarize(conversationId, agentKey))
                // 与标题改写同一手法：跑在 boundedElastic 上，不新增线程池、失败静默
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {
                }, error -> log.debug("会话 {} 的摘要生成失败：{}", conversationId, error.getMessage()));
    }

    /**
     * 真正的一次摘要生成：任何异常都在这里吞掉（调用方是 fire-and-forget 的异步任务）。
     */
    private void summarize(String conversationId, String agentKey) {
        try {
            // 记忆关闭的 Agent 根本不注入历史，也就用不到摘要——先看开关，别白花一次模型调用
            var agent = publishedAgentResolver.resolve(agentKey);
            if (agent == null || !agent.memoryEnabled()) {
                return;
            }
            var existing = summaryService.find(conversationId).orElse(null);
            if (existing != null && !intervalElapsed(existing)) {
                return;
            }
            var boundary = windowBoundary(conversationId);
            if (boundary == null) {
                // 会话还没超出窗口：没有需要压缩的内容
                return;
            }
            var after = existing == null ? null : existing.coveredUntilMessageId();
            if (after != null && after >= boundary) {
                // 摘要已经覆盖到窗口起点，等新内容出了窗口再说
                return;
            }
            var outside = messageService.findActiveBetween(conversationId, after, boundary,
                    properties.getMaxInputMessages());
            if (outside.size() < Math.max(properties.getMinNewMessages(), 1)) {
                return;
            }
            var modelId = AuxiliaryModel.resolve(agent);
            if (modelId == null) {
                return;
            }
            // 这条链路不记用量、不落事件，「用的哪个模型」只能靠日志归因
            log.debug("会话 {} 的摘要使用{}模型 modelId={}",
                    conversationId, AuxiliaryModel.isSecondary(agent) ? "次要" : "主", modelId);
            var messages = List.of(
                    Msg.builder().role(MsgRole.SYSTEM).textContent(prompt()).build(),
                    Msg.builder().role(MsgRole.USER).textContent(userPrompt(existing, outside)).build());
            var raw = modelChatClient.chatText(modelId, messages,
                    Duration.ofMillis(properties.getTimeoutMillis()));
            var summary = clean(raw);
            if (summary == null) {
                return;
            }
            var lastMessageId = outside.get(outside.size() - 1).messageId();
            if (lastMessageId == null) {
                return;
            }
            summaryService.upsertIfNewer(conversationId, summary, lastMessageId,
                    (existing == null ? 0 : existing.coveredMessages()) + outside.size(), modelId);
        } catch (Exception exception) {
            log.debug("会话 {} 的摘要生成失败（保留旧摘要）：{}", conversationId, exception.getMessage());
        }
    }

    /**
     * 历史窗口内最早那条消息的 id：它之前的内容就是「窗口外」。
     *
     * @return 边界消息 id；会话尚未超出窗口（没有窗口外内容）时返回 {@code null}
     */
    private Long windowBoundary(String conversationId) {
        var window = windowProperties.toWindow();
        var recent = messageService.findRecentByConversationId(conversationId, window.fetchLimit());
        if (recent.isEmpty()) {
            return null;
        }
        var kept = window.select(recent).messages();
        if (kept.isEmpty() || recent.size() <= kept.size()) {
            return null;
        }
        return kept.get(0).messageId();
    }

    /**
     * 距上一次摘要是否已过防抖间隔；没有更新时间（老数据）时视为可以生成。
     */
    private boolean intervalElapsed(ConversationSummaryDTO existing) {
        var updatedAt = existing.updatedAt();
        if (updatedAt == null) {
            return true;
        }
        return updatedAt.isBefore(OffsetDateTime.now().minusSeconds(Math.max(properties.getMinIntervalSeconds(), 0)));
    }

    private String prompt() {
        return """
                你是会话记忆压缩助手。把「更早的会话内容」压缩成一段可以长期携带的摘要，供后续对话参考。
                要求：
                - 保留：用户的目标与约束、已确认的结论与决策、关键实体（名称/编号/参数/数字/路径）、已完成的事项、未完成的事项；
                - 丢弃：寒暄、重复表述、与目标无关的细节、思考过程；
                - 只输出摘要正文：不要标题、不要「摘要：」之类的前缀、不要 Markdown 代码块、不要解释；
                - 不超过 %d 个字符，使用与原文相同的语言。
                """.formatted(properties.getMaxSummaryChars()).trim();
    }

    private String userPrompt(ConversationSummaryDTO existing, List<SessionMessage> messages) {
        var text = new StringBuilder();
        text.append("【已有摘要】\n");
        text.append(existing == null || existing.summary() == null || existing.summary().isBlank()
                ? "（无）" : existing.summary());
        text.append("\n\n【需要并入摘要的新内容】\n");
        for (SessionMessage message : messages) {
            var role = message.role() == MessageRole.USER ? "用户" : "助手";
            text.append('[').append(role).append("] ")
                    .append(truncate(message.content(), properties.getMaxInputCharsPerMessage()))
                    .append('\n');
        }
        return text.toString();
    }

    /**
     * 摘要清洗：去首尾空白、去掉模型可能加上的「摘要：」前缀、按上限硬截断。
     *
     * @return 干净的摘要；模型没给出有效内容时返回 {@code null}
     */
    private String clean(String raw) {
        if (raw == null) {
            return null;
        }
        var text = raw.trim();
        for (String prefix : List.of("摘要：", "摘要:", "会话摘要：", "会话摘要:")) {
            if (text.startsWith(prefix)) {
                text = text.substring(prefix.length()).trim();
            }
        }
        if (text.isEmpty()) {
            return null;
        }
        int max = Math.max(properties.getMaxSummaryChars(), 1);
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}
