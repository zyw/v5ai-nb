package xin.v5ai.nb.runtime.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.bo.ChatBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;
import xin.v5ai.nb.runtime.core.RunCancellationRegistry;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.core.service.RunRecordService;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 门户运行端点的入参校验、会话归属与停止对话语义。
 *
 * <p>本实例的归属判定一律以 API Key 为准（ADR 0006）：别人的会话统一 404，
 * 不泄露「存在但不属于你」。</p>
 */
class ChatControllerTest {

    private static final long API_KEY_ID = 1L;
    private static final long OWNER_USER_ID = 7L;
    private static final String AGENT_KEY = "a1";

    private AgentRuntime agentRuntime;
    private MessageService messageService;
    private ConversationService conversationService;
    private RunRecordService runRecordService;
    private RunCancellationRegistry cancellationRegistry;
    private PublishedAgentResolver publishedAgentResolver;
    private ModelImageSupportResolver imageSupportResolver;
    private ConversationSummaryService conversationSummaryService;
    private ChatController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        agentRuntime = mock(AgentRuntime.class);
        messageService = mock(MessageService.class);
        conversationService = mock(ConversationService.class);
        runRecordService = mock(RunRecordService.class);
        cancellationRegistry = new RunCancellationRegistry();
        publishedAgentResolver = mock(PublishedAgentResolver.class);
        imageSupportResolver = mock(ModelImageSupportResolver.class);
        conversationSummaryService = mock(ConversationSummaryService.class);
        controller = new ChatController(agentRuntime, messageService, conversationService, runRecordService,
                cancellationRegistry, publishedAgentResolver, imageSupportResolver, conversationSummaryService);

        request = mock(HttpServletRequest.class);
        when(request.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE))
                .thenReturn(new ApiKeysAuthDTO(API_KEY_ID, OWNER_USER_ID, "门户Key", "track-1", true, Set.of(AGENT_KEY)));
    }

    // ---------- 入参与门控 ----------

    @Test
    void attachmentsAreRejectedWhenTheModelDoesNotSupportImages() {
        when(publishedAgentResolver.resolve(AGENT_KEY)).thenReturn(agent(1L));
        when(imageSupportResolver.supportsImageInput(1L)).thenReturn(false);
        var bo = chat("看图", List.of(AttachmentRef.image(5L)));

        assertThat(failure(bo).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(agentRuntime, never()).stream(any());
    }

    @Test
    void attachmentsAreAcceptedWhenTheModelDeclaresImageCapability() {
        when(publishedAgentResolver.resolve(AGENT_KEY)).thenReturn(agent(1L));
        when(imageSupportResolver.supportsImageInput(1L)).thenReturn(true);
        when(agentRuntime.stream(any())).thenReturn(Flux.<RuntimeRunEventDTO>empty());

        controller.stream(AGENT_KEY, chat("看图", List.of(AttachmentRef.image(5L))), request);

        verify(agentRuntime).stream(any());
    }

    @Test
    void overlongOrIllegalConversationIdIsRejectedBeforeItReachesTheDatabase() {
        assertThat(failure(chatWithId("x".repeat(37))).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(failure(chatWithId("has space")).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        // 空值沿用「后端自动生成会话」的既有行为，不算错误
        when(agentRuntime.stream(any())).thenReturn(Flux.empty());
        controller.stream(AGENT_KEY, chatWithId(null), request);
        verify(agentRuntime).stream(any());
    }

    @Test
    void missingApiKeyContextIsUnauthorized() {
        var anonymous = mock(HttpServletRequest.class);

        assertThat(catchThrowableOfType(() -> controller.stream(AGENT_KEY, chat("q"), anonymous),
                V5aiException.class).code()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ---------- 会话列表 / 改名 / 归档 ----------

    /**
     * 会话列表只读会话表：名称在会话创建时就由首条提问写好（见 ConversationNaming），
     * 因此一条消息表查询都不该发生——消息表一大，那种「每个会话查一次首条提问」的兜底就是全表扫。
     */
    @Test
    void conversationListIsScopedToThisKeyAndNeverTouchesTheMessageTable() {
        var named = conversation("c-1", "我的会话", null);
        var unnamed = conversation("c-2", null, null);
        when(conversationService.list(AGENT_KEY, API_KEY_ID, false)).thenReturn(List.of(named, unnamed));

        var rows = controller.conversations(AGENT_KEY, false, request).getData();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).conversationId()).isEqualTo("c-1");
        assertThat(rows.get(0).name()).isEqualTo("我的会话");
        assertThat(rows.get(0).archived()).isFalse();
        // 提问为空的会话（如纯图片提问）才会是 null，前端显示「新会话」
        assertThat(rows.get(1).name()).isNull();
        verifyNoInteractions(messageService);
    }

    @Test
    void archivedFilterIsPassedThroughToTheQuery() {
        when(conversationService.list(AGENT_KEY, API_KEY_ID, true)).thenReturn(List.of());

        controller.conversations(AGENT_KEY, true, request);

        verify(conversationService).list(AGENT_KEY, API_KEY_ID, true);
    }

    @Test
    void renameTrimsTheNameAndArchiveTogglesTheFlag() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));

        controller.updateConversation(AGENT_KEY, "c-1",
                new ChatController.ConversationUpdateBo("  新名字  ", true), request);
        verify(conversationService).rename("c-1", "新名字");
        verify(conversationService).setArchived("c-1", true);

        controller.updateConversation(AGENT_KEY, "c-1",
                new ChatController.ConversationUpdateBo(null, false), request);
        verify(conversationService).setArchived("c-1", false);
    }

    @Test
    void blankOrTooLongConversationNameIsRejected() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));

        assertThat(catchThrowableOfType(() -> controller.updateConversation(AGENT_KEY, "c-1",
                new ChatController.ConversationUpdateBo("   ", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(catchThrowableOfType(() -> controller.updateConversation(AGENT_KEY, "c-1",
                new ChatController.ConversationUpdateBo("x".repeat(101), null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(conversationService, never()).rename(anyString(), anyString());
    }

    // ---------- 归属断言 ----------

    @Test
    void anotherKeysConversationIsNotFoundForReadRenameAndMessages() {
        when(conversationService.findById("c-9"))
                .thenReturn(Optional.of(new ConversationDTO("c-9", AGENT_KEY, 99L, OWNER_USER_ID,
                        null, null, null, null)));

        assertThat(catchThrowableOfType(() -> controller.conversationMessages(AGENT_KEY, "c-9", request),
                V5aiException.class).code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(catchThrowableOfType(() -> controller.updateConversation(AGENT_KEY, "c-9",
                new ChatController.ConversationUpdateBo("x", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(conversationService, never()).rename(anyString(), anyString());
    }

    @Test
    void conversationOfAnotherAgentIsNotFound() {
        when(conversationService.findById("c-1"))
                .thenReturn(Optional.of(new ConversationDTO("c-1", "other-agent", API_KEY_ID, OWNER_USER_ID,
                        null, null, null, null)));

        assertThat(catchThrowableOfType(() -> controller.conversationMessages(AGENT_KEY, "c-1", request),
                V5aiException.class).code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void messagesOfOwnConversationCarryAttachmentAccessUrls() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", "我的", null)));
        when(messageService.findByConversationId("c-1")).thenReturn(List.of(
                new SessionMessage("c-1", AGENT_KEY, MessageRole.USER, "看图",
                        List.of(AttachmentRef.image(42L))).withMessageId(1L),
                new SessionMessage("c-1", AGENT_KEY, MessageRole.ASSISTANT, "看到了")
                        .withReasoning("先看图再回答").withMessageId(2L)));

        var messages = controller.conversationMessages(AGENT_KEY, "c-1", request).getData();

        assertThat(messages).hasSize(2);
        // 思考随历史一起交给门户（折叠展示），用户消息没有思考
        assertThat(messages.get(0).reasoning()).isNull();
        assertThat(messages.get(1).reasoning()).isEqualTo("先看图再回答");
        assertThat(messages.get(0).attachments()).singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.type()).isEqualTo("IMAGE");
                    assertThat(attachment.resourceId()).isEqualTo(42L);
                    assertThat(attachment.accessUrl()).isEqualTo("/api/v1/agents/attachments/42");
                });
        assertThat(messages.get(1).attachments()).isEmpty();
        // 主键要交给前端：它是「重新生成」的锚点
        assertThat(messages.get(0).messageId()).isEqualTo(1L);
        assertThat(messages.get(1).messageId()).isEqualTo(2L);
    }

    /** 引用随历史一起交给门户：与实时流 RETRIEVAL 载荷同构的六个字段，少一个前端渲染器就失配。 */
    @Test
    void messagesOfOwnConversationCarryCitations() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", "我的", null)));
        when(messageService.findByConversationId("c-1")).thenReturn(List.of(
                new SessionMessage("c-1", AGENT_KEY, MessageRole.USER, "问题").withMessageId(1L),
                new SessionMessage("c-1", AGENT_KEY, MessageRole.ASSISTANT, "回答")
                        .withCitations(List.of(new RagHit(5L, 13L, "文档.pdf", 20, "切片内容", 0.21)))
                        .withMessageId(2L)));

        var messages = controller.conversationMessages(AGENT_KEY, "c-1", request).getData();

        assertThat(messages.get(0).citations()).isEmpty();
        assertThat(messages.get(1).citations()).singleElement().satisfies(citation -> {
            assertThat(citation.knowledgeBaseId()).isEqualTo(5L);
            assertThat(citation.documentId()).isEqualTo(13L);
            assertThat(citation.documentTitle()).isEqualTo("文档.pdf");
            assertThat(citation.chunkIndex()).isEqualTo(20);
            assertThat(citation.content()).isEqualTo("切片内容");
            assertThat(citation.score()).isEqualTo(0.21);
        });
    }

    // ---------- 重新生成 ----------

    @Test
    void regenerateSupersedesTheOldAnswerThenReRunsTheSameQuestion() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));
        when(messageService.findActiveMessage(55L)).thenReturn(Optional.of(
                new SessionMessage("c-1", AGENT_KEY, MessageRole.USER, "原来的提问",
                        List.of(AttachmentRef.image(42L))).withMessageId(55L)));
        when(publishedAgentResolver.resolve(AGENT_KEY)).thenReturn(agent(1L));
        when(imageSupportResolver.supportsImageInput(1L)).thenReturn(true);
        when(agentRuntime.stream(any())).thenReturn(Flux.empty());

        controller.regenerate(AGENT_KEY, "c-1", new ChatController.RegenerateBo("55", false), request);

        // 先作废锚点之后的消息（旧回答），再复用这条提问重跑；
        // 覆盖了被作废内容的摘要必须同时失效，否则模型会继续「记得」旧回答
        verify(messageService).supersedeAfter("c-1", 55L);
        verify(conversationSummaryService).invalidateIfCoveringAfter("c-1", 55L);
        var captor = ArgumentCaptor.forClass(AgentRunBo.class);
        verify(agentRuntime).stream(captor.capture());
        var bo = captor.getValue();
        assertThat(bo.query()).isEqualTo("原来的提问");
        assertThat(bo.attachments()).containsExactly(AttachmentRef.image(42L));
        assertThat(bo.reuseUserMessage()).isTrue();
        assertThat(bo.agentKey()).isEqualTo(AGENT_KEY);
        assertThat(bo.apiKeyId()).isEqualTo(API_KEY_ID);
    }

    @Test
    void regenerateRejectsMissingAnchorWithoutTouchingAnything() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));
        when(messageService.findActiveMessage(55L)).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(() -> controller.regenerate(AGENT_KEY, "c-1",
                new ChatController.RegenerateBo("55", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(messageService, never()).supersedeAfter(anyString(), anyLong());
        verify(agentRuntime, never()).stream(any());
    }

    /** 锚点必须是**本会话的**用户提问：别的会话的提问、或本会话的助手回答都不行。 */
    @Test
    void regenerateRejectsAnchorsFromAnotherConversationOrAssistantMessages() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));
        when(messageService.findActiveMessage(55L)).thenReturn(Optional.of(new SessionMessage(
                "other-conversation", AGENT_KEY, MessageRole.USER, "别人的提问").withMessageId(55L)));
        when(messageService.findActiveMessage(56L)).thenReturn(Optional.of(new SessionMessage(
                "c-1", AGENT_KEY, MessageRole.ASSISTANT, "回答").withMessageId(56L)));

        assertThat(catchThrowableOfType(() -> controller.regenerate(AGENT_KEY, "c-1",
                new ChatController.RegenerateBo("55", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(catchThrowableOfType(() -> controller.regenerate(AGENT_KEY, "c-1",
                new ChatController.RegenerateBo("56", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(messageService, never()).supersedeAfter(anyString(), anyLong());
    }

    /** 归档会话不能重新生成；且必须在作废之前就挡住，否则会先毁掉消息再失败。 */
    @Test
    void regenerateRejectsArchivedConversationBeforeSuperseding() {
        when(conversationService.findById("c-1"))
                .thenReturn(Optional.of(conversation("c-1", null, OffsetDateTime.now())));

        assertThat(catchThrowableOfType(() -> controller.regenerate(AGENT_KEY, "c-1",
                new ChatController.RegenerateBo("55", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(messageService, never()).supersedeAfter(anyString(), anyLong());
    }

    /** 锚点那轮带图但当前模型不支持 → 400，同样发生在作废之前。 */
    @Test
    void regenerateRejectsImagesWhenTheModelDoesNotSupportThem() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));
        when(messageService.findActiveMessage(55L)).thenReturn(Optional.of(new SessionMessage(
                "c-1", AGENT_KEY, MessageRole.USER, "看图",
                List.of(AttachmentRef.image(42L))).withMessageId(55L)));
        when(publishedAgentResolver.resolve(AGENT_KEY)).thenReturn(agent(1L));
        when(imageSupportResolver.supportsImageInput(1L)).thenReturn(false);

        assertThat(catchThrowableOfType(() -> controller.regenerate(AGENT_KEY, "c-1",
                new ChatController.RegenerateBo("55", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(messageService, never()).supersedeAfter(anyString(), anyLong());
    }

    /**
     * 锚点是雪花主键（19 位）：只能以**字符串**回传，原样送进查询，不能被转成数字
     * （转了就成 2101304707712417800，库里的 2101304707712417794 查不到 → 404「提问不存在」）。
     */
    @Test
    void regenerateAcceptsTheSnowflakeAnchorAsString() {
        var snowflake = "2101304707712417794";
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));
        when(messageService.findActiveMessage(2101304707712417794L)).thenReturn(Optional.of(
                new SessionMessage("c-1", AGENT_KEY, MessageRole.USER, "原来的提问")
                        .withMessageId(2101304707712417794L)));
        when(agentRuntime.stream(any())).thenReturn(Flux.empty());

        controller.regenerate(AGENT_KEY, "c-1", new ChatController.RegenerateBo(snowflake, false), request);

        // 逐位精确：锚点解析没有经过任何浮点/四舍五入
        verify(messageService).supersedeAfter("c-1", 2101304707712417794L);
        verify(messageService).findActiveMessage(2101304707712417794L);
    }

    /** 不是数字的锚点：400（调用方入参错误），而不是穿到下游变成 500。 */
    @Test
    void regenerateRejectsAMalformedAnchor() {
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));

        assertThat(catchThrowableOfType(() -> controller.regenerate(AGENT_KEY, "c-1",
                new ChatController.RegenerateBo("not-a-number", null), request), V5aiException.class).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(messageService, never()).supersedeAfter(anyString(), anyLong());
        verify(agentRuntime, never()).stream(any());
    }

    // ---------- 停止对话 ----------

    @Test
    void stopRunTriggersTheRegisteredSignalForOwnRun() {
        var signal = reactor.core.publisher.Sinks.<Void>empty();
        cancellationRegistry.register("run-1", signal);
        when(runRecordService.findConversationId("run-1")).thenReturn(Optional.of("c-1"));
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));

        controller.stopRun(AGENT_KEY, "run-1", request);

        assertThat(signal.asMono().block()).isNull();
        verify(runRecordService, never()).cancel(anyString());
    }

    /**
     * 本实例找不到取消信号（已结束、或流已断开）时不再报错，而是把库里仍是 RUNNING 的记录
     * 落定为 CANCELED —— 重复点停止、停一次已完成的运行都得到 200。
     */
    @Test
    void stopRunFallsBackToMarkingTheStoredRunCanceled() {
        when(runRecordService.findConversationId("run-1")).thenReturn(Optional.of("c-1"));
        when(conversationService.findById("c-1")).thenReturn(Optional.of(conversation("c-1", null, null)));

        controller.stopRun(AGENT_KEY, "run-1", request);

        verify(runRecordService).cancel("run-1");
    }

    @Test
    void stopRunOfAnUnknownRunOrAnotherKeysRunIsNotFound() {
        when(runRecordService.findConversationId("run-x")).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> controller.stopRun(AGENT_KEY, "run-x", request),
                V5aiException.class).code()).isEqualTo(ErrorCode.NOT_FOUND);

        when(runRecordService.findConversationId("run-2")).thenReturn(Optional.of("c-9"));
        when(conversationService.findById("c-9")).thenReturn(Optional.of(new ConversationDTO("c-9", AGENT_KEY,
                99L, OWNER_USER_ID, null, null, null, null)));
        assertThat(catchThrowableOfType(() -> controller.stopRun(AGENT_KEY, "run-2", request),
                V5aiException.class).code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------- helpers ----------

    private ChatBo chat(String query) {
        return chatWithId("c-1", query, List.of());
    }

    private ChatBo chat(String query, List<AttachmentRef> attachments) {
        return chatWithId("c-1", query, attachments);
    }

    private ChatBo chatWithId(String conversationId) {
        return chatWithId(conversationId, "q", List.of());
    }

    private ChatBo chatWithId(String conversationId, String query, List<AttachmentRef> attachments) {
        return new ChatBo(conversationId, query, null, attachments, List.of(), List.of());
    }

    private V5aiException failure(ChatBo bo) {
        return catchThrowableOfType(() -> controller.stream(AGENT_KEY, bo, request), V5aiException.class);
    }

    private ConversationDTO conversation(String id, String name, OffsetDateTime archivedAt) {
        return new ConversationDTO(id, AGENT_KEY, API_KEY_ID, OWNER_USER_ID, name, archivedAt,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private static AgentDTO agent(Long modelId) {
        return new AgentDTO(AGENT_KEY, "Demo", "desc", AgentStatus.PUBLISHED, modelId, 1L,
                "sys", null, null, null, false, false, false, false, false, RagCallMode.FORCED.value(),
                null, true);
    }
}
