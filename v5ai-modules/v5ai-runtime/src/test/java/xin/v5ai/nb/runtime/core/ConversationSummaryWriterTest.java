package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.runtime.core.config.properties.ConversationSummaryProperties;
import xin.v5ai.nb.runtime.core.config.properties.HistoryWindowProperties;
import xin.v5ai.nb.runtime.core.domain.ConversationSummaryDTO;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.runtime.core.service.MessageService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话摘要的触发口径：只压「历史窗口之外」的内容、只在值得压时压、失败静默、水位只前进。
 */
class ConversationSummaryWriterTest {

    private PublishedAgentResolver agentResolver;
    private ModelChatClient modelChatClient;
    private MessageService messageService;
    private ConversationSummaryService summaryService;
    private ConversationSummaryProperties properties;
    private ConversationSummaryWriter writer;

    @BeforeEach
    void setUp() {
        agentResolver = mock(PublishedAgentResolver.class);
        modelChatClient = mock(ModelChatClient.class);
        messageService = mock(MessageService.class);
        summaryService = mock(ConversationSummaryService.class);
        properties = new ConversationSummaryProperties();
        properties.setMinNewMessages(2);
        var windowProperties = new HistoryWindowProperties();
        windowProperties.setMaxMessages(2);
        windowProperties.setMinMessages(1);
        windowProperties.setAlignToUserTurn(false);
        writer = new ConversationSummaryWriter(agentResolver, modelChatClient, messageService, summaryService,
                properties, windowProperties);
        when(summaryService.find("c1")).thenReturn(Optional.empty());
        when(agentResolver.resolve("agent-x")).thenReturn(agent(true));
    }

    private static SessionMessage message(long id, MessageRole role, String content) {
        return new SessionMessage("c1", "agent-x", role, content).withMessageId(id);
    }

    /** 默认开启记忆——摘要只为「会注入历史」的 Agent 生成。 */
    private static AgentDTO agent(boolean memoryEnabled) {
        return agent(memoryEnabled, 7L, null);
    }

    private static AgentDTO agent(boolean memoryEnabled, Long secondaryModelId) {
        return agent(memoryEnabled, 7L, secondaryModelId);
    }

    private static AgentDTO agent(boolean memoryEnabled, Long modelId, Long secondaryModelId) {
        return new AgentDTO("agent-x", "Demo", "desc", null, modelId, null,
                null, null, null, null, memoryEnabled, false, false, false, false,
                RagCallMode.FORCED.value(), secondaryModelId, true);
    }

    /** 四条历史、窗口只留两条：11/12 落在窗口外。 */
    private void givenFourMessages() {
        when(messageService.findRecentByConversationId(eq("c1"), anyInt())).thenReturn(List.of(
                message(11, MessageRole.USER, "问5"),
                message(12, MessageRole.ASSISTANT, "答5"),
                message(13, MessageRole.USER, "问6"),
                message(14, MessageRole.ASSISTANT, "答6")));
        when(messageService.findActiveBetween(eq("c1"), any(), eq(13L), anyInt())).thenReturn(List.of(
                message(11, MessageRole.USER, "问5"),
                message(12, MessageRole.ASSISTANT, "答5")));
    }

    /** 窗口的取数上限决定 SQL 一次读多少条：2 条窗口 → 2×2 与下限 20 取大。 */
    @Test
    void theWindowAsksTheRepositoryForTwiceItsBudget() {
        var windowProperties = new HistoryWindowProperties();
        windowProperties.setMaxMessages(2);
        org.assertj.core.api.Assertions.assertThat(windowProperties.toWindow().fetchLimit()).isEqualTo(20);
    }

    @Test
    void shortConversationNeverCallsTheModel() {
        when(messageService.findRecentByConversationId(eq("c1"), anyInt())).thenReturn(List.of(
                message(1, MessageRole.USER, "问"),
                message(2, MessageRole.ASSISTANT, "答")));

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }

    @Test
    void newMessagesOutsideTheWindowAreCompressedAndTheWatermarkAdvances() {
        givenFourMessages();
        // 模型可能带上「摘要：」前缀与多余空白，写入前必须清洗
        when(modelChatClient.chatText(eq(7L), anyList(), any(Duration.class)))
                .thenReturn("  摘要：用户问了 5 和 6  ");

        writer.summarizeIfNeeded("c1", "agent-x");

        verify(summaryService, timeout(3000))
                .upsertIfNewer("c1", "用户问了 5 和 6", 12L, 2, 7L);
        // SQL 侧一次取多少条：窗口 2 条 → 2*2 与下限 20 取大（失败报告里会给出实际入参）
        verify(messageService).findRecentByConversationId("c1", 20);
    }

    @Test
    void aFewerThanTheThresholdOfNewMessagesIsNotWorthAnLlmCall() {
        when(messageService.findRecentByConversationId(eq("c1"), anyInt())).thenReturn(List.of(
                message(11, MessageRole.USER, "问5"),
                message(12, MessageRole.ASSISTANT, "答5"),
                message(13, MessageRole.USER, "问6"),
                message(14, MessageRole.ASSISTANT, "答6")));
        when(messageService.findActiveBetween(eq("c1"), any(), eq(13L), anyInt()))
                .thenReturn(List.of(message(12, MessageRole.ASSISTANT, "答5")));

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }

    @Test
    void whenTheWatermarkAlreadyReachesTheWindowStartNothingHappens() {
        givenFourMessages();
        when(summaryService.find("c1")).thenReturn(Optional.of(new ConversationSummaryDTO(
                "c1", "旧摘要", 13L, 2, 7L, OffsetDateTime.now().minusMinutes(5))));

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }

    @Test
    void summariesAreDebouncedWithinTheConfiguredInterval() {
        givenFourMessages();
        when(summaryService.find("c1")).thenReturn(Optional.of(new ConversationSummaryDTO(
                "c1", "刚生成的摘要", 5L, 2, 7L, OffsetDateTime.now())));

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }

    @Test
    void modelFailuresAreSwallowedAndTheOldSummaryStays() {
        givenFourMessages();
        when(modelChatClient.chatText(eq(7L), anyList(), any(Duration.class)))
                .thenThrow(new IllegalStateException("model down"));

        writer.summarizeIfNeeded("c1", "agent-x");

        verify(summaryService, after(400).never())
                .upsertIfNewer(any(), any(), anyLong(), anyInt(), any());
    }

    /** 配了次要模型就用它——这是本项功能的落点（标题与摘要共用同一个字段）。 */
    @Test
    void aSecondaryModelOverridesTheAgentModel() {
        givenFourMessages();
        when(agentResolver.resolve("agent-x")).thenReturn(agent(true, 99L));
        when(modelChatClient.chatText(eq(99L), anyList(), any(Duration.class))).thenReturn("摘要正文");

        writer.summarizeIfNeeded("c1", "agent-x");

        verify(modelChatClient, timeout(3000)).chatText(eq(99L), anyList(), any(Duration.class));
    }

    /** 没配次要模型就回退绑定的对话模型（存量 Agent 行为不变）。 */
    @Test
    void withoutASecondaryModelTheAgentModelIsUsed() {
        givenFourMessages();
        when(modelChatClient.chatText(eq(7L), anyList(), any(Duration.class))).thenReturn("摘要正文");

        writer.summarizeIfNeeded("c1", "agent-x");

        verify(modelChatClient, timeout(3000)).chatText(eq(7L), anyList(), any(Duration.class));
    }

    /**
     * 两个模型都没有：没有可调用的模型，一次模型调用都不该发。
     *
     * <p>解析链收口之前这里是「主模型为空时抛异常 → 被 catch 吞成 debug 日志」，
     * 行为上同样不落摘要，但会走一遍异常构造；现在直接短路。</p>
     */
    @Test
    void withNoModelAtAllTheModelIsNeverCalled() {
        givenFourMessages();
        when(agentResolver.resolve("agent-x")).thenReturn(agent(true, null, null));

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }

    /** 记忆关闭的 Agent 不注入历史，摘要对它毫无用处：一次模型调用都不该发。 */
    @Test
    void agentsWithoutMemoryNeverGenerateSummaries() {
        givenFourMessages();
        when(agentResolver.resolve("agent-x")).thenReturn(agent(false));

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }

    @Test
    void disabledSummaryDoesNothing() {
        givenFourMessages();
        properties.setEnabled(false);

        writer.summarizeIfNeeded("c1", "agent-x");

        verifyNoInteractions(modelChatClient);
    }
}
