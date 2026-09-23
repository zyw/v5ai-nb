package xin.v5ai.nb.runtime.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.RunUsage;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.domain.bo.ChatBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;
import xin.v5ai.nb.runtime.core.utils.ConversationNaming;
import xin.v5ai.nb.runtime.core.RunCancellationRegistry;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.core.service.RunRecordService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 门户运行入口（{@code /api/v1/agents/{agentKey}/chat/**}）：对话、历史回放与会话管理。
 *
 * <p>鉴权由 {@code AgentApiKeysServletFilter} 完成（API Key + 该 Key 到 agentKey 的绑定 + 配额），
 * 结果放在请求属性里；本控制器只做参数校验、会话归属判定与入参装配。</p>
 *
 * <p>会话的归属主体是 API Key（见 docs/adr/0006-api-key-scoped-conversations.md）：
 * {@code apiKeyId} 一律从鉴权结果注入运行入参，**绝不接受请求体传入**。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agents")
public class ChatController {

    /**
     * 会话 ID 的长度上限：与 {@code v5ai_conversation.id VARCHAR(36)} 一致。
     * 超长 ID 过去会直接撞库变成 500，现在在校验层就拒绝。
     */
    private static final int MAX_CONVERSATION_ID_LENGTH = 36;

    /**
     * 允许的会话 ID 字符集：调用方生成、会被放进 URL 路径，限制在安全的无歧义字符内。
     */
    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,36}$");

    /**
     * 会话名称长度上限：与 {@code v5ai_conversation.name VARCHAR(100)}、
     * 写入侧的 {@link ConversationNaming#MAX_NAME_LENGTH} 是同一处定义。
     */
    private static final int MAX_CONVERSATION_NAME_LENGTH = ConversationNaming.MAX_NAME_LENGTH;

    /**
     * 附件读取地址前缀：与会话消息里返回的 accessUrl 同源。
     */
    private static final String ATTACHMENT_URL_PREFIX = "/api/v1/agents/attachments/";

    private final AgentRuntime agentRuntime;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final RunRecordService runRecordService;
    private final RunCancellationRegistry cancellationRegistry;
    private final PublishedAgentResolver publishedAgentResolver;
    private final ModelImageSupportResolver modelImageSupportResolver;
    private final ConversationSummaryService conversationSummaryService;

    @PostMapping("/{agentKey}/chat/stream")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatBo bo,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        assertConversationIdValid(bo);
        assertAttachmentsSupported(agentKey, bo.attachments());
        return toSse(agentRuntime.stream(toRunBo(agentKey, bo, auth)));
    }

    @PostMapping("/{agentKey}/chat")
    public Mono<AgentRunVo> call(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatBo bo,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        assertConversationIdValid(bo);
        assertAttachmentsSupported(agentKey, bo.attachments());
        return agentRuntime.call(toRunBo(agentKey, bo, auth));
    }

    /**
     * 会话消息历史（含每条消息的附件）。
     */
    @GetMapping("/{agentKey}/chat/conversations/{conversationId}")
    public R<List<ConversationMessageResult>> conversationMessages(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            HttpServletRequest request
    ) {
        requireOwnedConversation(agentKey, conversationId, requireAuth(request));
        var list = messageService.findByConversationId(conversationId).stream()
                .map(ConversationMessageResult::from)
                .toList();
        return R.ok(list);
    }

    /**
     * 在指定会话里继续对话（SSE）。
     *
     * <p>与 {@code /chat/stream} 的唯一区别是会话从**路径**取：历史在订阅之前就由服务端读出，
     * 作为**显式历史**装进运行入参（{@link AgentRunBo#withHistory}）。因此这条链路不依赖
     * {@code memoryEnabled} 的自动记忆注入——入参里已经有历史，{@code AgentScopeRuntime.withMemory}
     * 会直接短路（不查库、也不再剔除尾部提问），模型的上下文就是这里读到的那份。</p>
     *
     * <p>{@link #toRunBo} 组出来的入参里 {@code conversationId} 取自请求体，紧接着被路径参数覆盖
     * （{@link AgentRunBo#withConversationId(String)}）：**路径是唯一权威**，请求体里同名传什么都不作数。</p>
     *
     * <p>归属与归档校验不在这里：下游 {@code PersistingAgentRuntime} 订阅时经
     * {@code ensureConversation} 统一拦截（{@code api_key_id} 不匹配与不存在同为 404、已归档为 409），
     * 会话不存在则按上述入参新建——与 {@code /chat/stream} 首次调用是同一行为。控制器只前置校验
     * 「附件与模型能力」（{@link #assertAttachmentsSupported}），避免把图片悄悄丢掉。</p>
     *
     * @param agentKey       Agent 标识；到 Key 的绑定校验由 {@code AgentApiKeysServletFilter} 完成
     * @param conversationId 会话 ID，取自 URL 路径
     * @param bo             本次运行的请求体：提问、联网意图、附件与收窄项；
     *                       其中的 {@code conversationId} 会被路径参数覆盖
     * @return 与 {@code /chat/stream} 相同的事件流（RUN_STARTED → … → RUN_COMPLETED）
     */
    @PostMapping("/{agentKey}/chat/conversations/{conversationId}/resume")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> resume(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ChatBo bo,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        assertAttachmentsSupported(agentKey, bo.attachments());
        var history = messageService.findByConversationId(conversationId);
        var resumeRequest = toRunBo(agentKey, bo, auth)
                .withConversationId(conversationId)
                .withHistory(history);
        return toSse(agentRuntime.stream(resumeRequest));
    }

    /**
     * 重新生成某一轮的回答。
     *
     * <p>「重新生成」在服务端是**作废 + 复用提问重跑**：把该轮提问之后的消息全部打上作废标记
     * （软状态，数据保留、可审计），然后复用那条提问再跑一次。提问行保持有效、不重新插入，
     * 因此历史里不会出现两条相同的提问；作废的回答也不再进入展示与记忆回放。</p>
     *
     * <p>顺序是刻意的：<b>所有校验都在作废之前</b>（任何拒绝都不产生破坏），
     * 而作废在重跑之前（否则被替换的旧回答会被 {@code withMemory} 读进模型上下文）。</p>
     *
     * @param bo {@code {fromMessageId: 该轮提问的消息 id, webSearch?}}
     * @return 与 {@code /chat/stream} 相同的事件流
     */
    @PostMapping("/{agentKey}/chat/conversations/{conversationId}/regenerate")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> regenerate(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody RegenerateBo bo,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        assertConversationNotArchived(requireOwnedConversation(agentKey, conversationId, auth));
        // 锚点必须是「本会话里未作废的用户提问」：不满足一律 404，不泄露存在性
        var anchor = messageService.findActiveMessage(parseMessageId(bo.fromMessageId()))
                .filter(message -> conversationId.equals(message.conversationId()))
                .filter(message -> message.role() == MessageRole.USER)
                .orElseThrow(() -> new V5aiException(ErrorCode.NOT_FOUND, "提问不存在"));
        assertAttachmentsSupported(agentKey, anchor.attachments());
        // 校验全部通过：先作废被替换的回答（及其后更晚的整轮问答），再复用这条提问重跑
        messageService.supersedeAfter(conversationId, anchor.messageId());
        // 会话摘要可能覆盖了刚被作废的问答：整条失效，避免模型继续「记得」已被替换的内容
        // （下次运行会按当前有效历史重新生成摘要）
        conversationSummaryService.invalidateIfCoveringAfter(conversationId, anchor.messageId());
        var regenerateRequest = new AgentRunBo(agentKey, conversationId, anchor.content())
                .withWebSearch(bo.webSearch())
                .withAttachments(anchor.attachments())
                .withReuseUserMessage(true)
                .withCaller(auth.apiKeyId(), auth.userId());
        return toSse(agentRuntime.stream(regenerateRequest));
    }

    /**
     * 继续对话的前置校验：归档是收尾态，不得再产生新的问答（见 CONTEXT.md「归档」）。
     *
     * <p>普通运行端点靠持久化装饰器里的 {@code ensureConversation} 统一拦截就够了；
     * 但「重新生成」会先作废旧消息，所以必须在这里、在动手之前挡掉。</p>
     */
    private void assertConversationNotArchived(ConversationDTO conversation) {
        if (conversation.archived()) {
            throw new V5aiException(ErrorCode.CONFLICT, "会话已归档，请先取消归档再继续对话");
        }
    }

    /**
     * 解析请求体里的消息 id。入参是**字符串**：消息主键是 19 位雪花号，超出 JS 安全整数，
     * 数字形态过一趟浏览器就会被四舍五入成另一个 id（见
     * {@code PersistingAgentRuntime#attachUserMessageId}），前端只能按字符串回传。
     *
     * <p>空值返回 {@code null}——「没传」与「传错」不是一回事：前者交给调用方按
     * 「找不到这条提问」处理（保持原有 404 语义），后者是入参错误，报 400 而不是穿到下游变 500。</p>
     */
    private static Long parseMessageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "提问标识无效");
        }
    }

    /**
     * 会话列表：限「本 Key + 本 Agent」，按最近活跃倒序。
     *
     * <p>只读 {@code v5ai_conversation} 一张表：名称在会话创建时就由首条提问写好
     * （见 {@link ConversationNaming}），首轮结束后可能被模型改写成短标题。
     * 过去为了给未命名会话兜底，这里还要对每个会话 {@code DISTINCT ON} 查一次消息表取首条提问，
     * 消息表一大，列表就成了门户里最贵的接口。</p>
     *
     * @param archived true 查已归档会话，false（默认）查进行中的会话
     */
    @GetMapping("/{agentKey}/chat/conversations")
    public R<List<ConversationSummaryResult>> conversations(
            @PathVariable("agentKey") String agentKey,
            @RequestParam(value = "archived", required = false, defaultValue = "false") boolean archived,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        return R.ok(conversationService.list(agentKey, auth.apiKeyId(), archived).stream()
                .map(ConversationSummaryResult::from)
                .toList());
    }

    /**
     * 改名与归档共用一个端点：body 里出现哪个字段就执行哪个动作。
     */
    @PatchMapping("/{agentKey}/chat/conversations/{conversationId}")
    public R<Void> updateConversation(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ConversationUpdateBo bo,
            HttpServletRequest request
    ) {
        requireOwnedConversation(agentKey, conversationId, requireAuth(request));
        if (bo.name() != null) {
            var name = bo.name().trim();
            if (name.isEmpty() || name.length() > MAX_CONVERSATION_NAME_LENGTH) {
                throw new V5aiException(ErrorCode.INVALID_ARGUMENT,
                        "会话名称长度需在 1-" + MAX_CONVERSATION_NAME_LENGTH + " 之间");
            }
            conversationService.rename(conversationId, name);
        }
        if (bo.archived() != null) {
            conversationService.setArchived(conversationId, bo.archived());
        }
        return R.ok();
    }

    /**
     * 停止一次进行中的运行。
     *
     * <p>幂等：已完成/已取消（或不在本实例）的运行同样返回 200。本实例登记着这次运行的取消信号
     * 就触发它，由流的取消路径统一收尾（标记 CANCELED、落库半截回答、记用量）；
     * 找不到信号时直接把库里仍是 RUNNING 的记录落定为 CANCELED，避免残留的「永远运行中」。</p>
     */
    @PostMapping("/{agentKey}/chat/runs/{runId}/stop")
    public R<Void> stopRun(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("runId") String runId,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        // 归属校验：不校验的话，知道 runId 就能停掉别人的运行
        var conversationId = runRecordService.findConversationId(runId)
                .orElseThrow(() -> new V5aiException(ErrorCode.NOT_FOUND, "运行不存在"));
        requireOwnedConversation(agentKey, conversationId, auth);
        if (!cancellationRegistry.cancel(runId)) {
            runRecordService.cancel(runId);
        }
        return R.ok();
    }

    /**
     * 请求体 → 运行入参：联网意图、附件与收窄项透传；调用方身份来自鉴权结果而非请求体。
     */
    private AgentRunBo toRunBo(String agentKey, ChatBo bo, ApiKeysAuthDTO auth) {
        return new AgentRunBo(agentKey, bo.conversationId(), bo.query())
                .withWebSearch(bo.webSearch())
                .withAttachments(bo.attachments())
                .withDisabledMcpServerIds(bo.disabledMcpServerIds())
                .withDisabledSkillIds(bo.disabledSkillIds())
                .withCaller(auth.apiKeyId(), auth.userId());
    }

    /**
     * 取过滤器写入请求属性的 API Key 鉴权结果；缺失说明请求绕过了过滤器。
     */
    private ApiKeysAuthDTO requireAuth(HttpServletRequest request) {
        var auth = (ApiKeysAuthDTO) request.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE);
        if (auth == null) {
            throw new V5aiException(ErrorCode.UNAUTHORIZED, "API Key 无效");
        }
        return auth;
    }

    /**
     * 归属断言：会话必须属于「本 Key + 本 Agent」。
     *
     * <p>不匹配与不存在返回同一个 404——否则状态码本身就能用来探测别人的会话是否存在；
     * 调试入口产生的无主会话同样不可被 Key 认领。</p>
     */
    private ConversationDTO requireOwnedConversation(String agentKey, String conversationId, ApiKeysAuthDTO auth) {
        return conversationService.findById(conversationId)
                .filter(conversation -> agentKey.equals(conversation.agentKey()))
                .filter(conversation -> auth.apiKeyId() != null && auth.apiKeyId().equals(conversation.apiKeyId()))
                .orElseThrow(() -> {
                    log.warn("conversation {} is not accessible by api key {}", conversationId, auth.apiKeyId());
                    return new V5aiException(ErrorCode.NOT_FOUND, "会话不存在");
                });
    }

    /**
     * 会话 ID 校验：为空时由后端生成（沿用既有行为），非空时限制长度与字符集，
     * 避免超长/含非法字符的 ID 一路走到数据库才炸成 500。
     */
    private void assertConversationIdValid(ChatBo bo) {
        var conversationId = bo.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (!CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT,
                    "会话 ID 只能由 1-" + MAX_CONVERSATION_ID_LENGTH + " 位字母、数字、'.'、'_'、'-' 组成");
        }
    }

    /**
     * 附件门控：模型未声明支持图片输入（{@code config.capabilities} 不含 {@code image}）时，
     * 携带附件的请求直接拒绝，而不是把图片悄悄丢掉（见 CONTEXT.md「支持图片输入」）。
     */
    private void assertAttachmentsSupported(String agentKey, List<AttachmentRef> attachments) {
        if (attachments.isEmpty()) {
            return;
        }
        var agent = publishedAgentResolver.resolve(agentKey);
        if (!modelImageSupportResolver.supportsImageInput(agent.modelId())) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "当前 Agent 绑定的模型不支持图片输入");
        }
    }

    private Flux<ServerSentEvent<RuntimeRunEventDTO>> toSse(Flux<RuntimeRunEventDTO> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name())
                .id(event.runId())
                .build());
    }

    /**
     * 会话消息的 API 响应体（不可变 record）。
     *
     * @param role        消息角色（USER/ASSISTANT）
     * @param content     消息内容（正文）
     * @param reasoning   模型的思考过程（仅助手消息、且当时开启了持久化时有值）；严格属于回看素材，
     *                    不会被回放给模型
     * @param citations   本条回答当时引用的切片（仅助手消息有值，无引用即空列表）；同样只供回看，
     *                    与实时流 {@code RETRIEVAL} 载荷的内容一致（见 docs/adr/0009）
     * @param attachments 消息携带的附件（图片）
     * @param usage       该回答的用量与用时；用户消息、或没有记录的历史消息为 null
     * @param messageId   消息主键：前端用它作为「重新生成」的锚点（该轮提问的消息 id）
     */
    public record ConversationMessageResult(MessageRole role, String content, String reasoning,
                                            List<CitationResult> citations,
                                            List<MessageAttachmentResult> attachments,
                                            RunUsage usage, Long messageId) {
        /**
         * 将领域消息 {@link SessionMessage} 转为 API 响应体（去掉会话等内部字段）。
         */
        public static ConversationMessageResult from(SessionMessage message) {
            return new ConversationMessageResult(message.role(), message.content(), message.reasoning(),
                    message.citations().stream().map(CitationResult::from).toList(),
                    message.attachments().stream().map(MessageAttachmentResult::from).toList(),
                    message.usage(), message.messageId());
        }
    }

    /**
     * 引用的 API 响应体：与实时流 {@code RETRIEVAL} 载荷**同构**的六个字段，门户因此能复用
     * 实时那套解析器与渲染组件（见 CONTEXT.md「引用」）。
     *
     * @param knowledgeBaseId 来源知识库 ID（载荷不带知识库名）
     * @param documentId      来源文档 ID
     * @param documentTitle   来源文档标题（未落库的文档回退为 doc-{id}）
     * @param chunkIndex      切片在文档内的序号
     * @param content         切片内容（落库时已按 2000 字符截断）
     * @param score           相似度得分
     */
    public record CitationResult(Long knowledgeBaseId, Long documentId, String documentTitle,
                                 Integer chunkIndex, String content, Double score) {
        static CitationResult from(RagHit hit) {
            return new CitationResult(hit.knowledgeBaseId(), hit.documentId(), hit.documentTitle(),
                    hit.chunkIndex(), hit.content(), hit.score());
        }
    }

    /**
     * 消息附件的 API 响应体。
     *
     * @param type       附件类型（当前只有 IMAGE）
     * @param resourceId 资源 id（随消息提交时用）
     * @param accessUrl  门户读取地址（&lt;img&gt; 带不了 Authorization 头，门户需按此地址取 blob）
     */
    public record MessageAttachmentResult(String type, Long resourceId, String accessUrl) {
        static MessageAttachmentResult from(AttachmentRef attachment) {
            return new MessageAttachmentResult(attachment.type(), attachment.resourceId(),
                    attachment.resourceId() == null ? null : ATTACHMENT_URL_PREFIX + attachment.resourceId());
        }
    }

    /**
     * 会话列表行（不可变 record）。
     *
     * @param conversationId 会话 ID
     * @param name           会话名称：新建会话时由首条提问生成，首轮结束后可能被模型改写；
     *                       仅「提问为空」（如纯图片提问）的会话为 null
     * @param agentKey       Agent 标识
     * @param userId         归属用户（展示冗余）
     * @param archived       是否已归档
     * @param createdAt      创建时间
     * @param updatedAt      最近活跃时间
     */
    public record ConversationSummaryResult(String conversationId, String name, String agentKey, Long userId,
                                            boolean archived, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static ConversationSummaryResult from(ConversationDTO conversation) {
            return new ConversationSummaryResult(conversation.conversationId(), conversation.name(),
                    conversation.agentKey(), conversation.userId(), conversation.archived(),
                    conversation.createdAt(), conversation.updatedAt());
        }
    }

    /**
     * 重新生成的请求体（不可变 record）。
     *
     * @param fromMessageId 该轮提问的消息 id（历史消息里的 {@code messageId}、或本轮
     *                      {@code RUN_STARTED} 载荷的 {@code userMessageId}）。
     *                      <b>是字符串</b>：消息主键是 19 位雪花号，超出 JS 安全整数，
     *                      数字形态的 JSON 会被浏览器四舍五入成另一个 id（表现为 404「提问不存在」），
     *                      所以服务端以字符串给出、也以字符串接收；数字形态的旧调用方照样能用
     * @param webSearch     本次是否联网：{@code null}=按 Agent 配置
     */
    public record RegenerateBo(String fromMessageId, Boolean webSearch) {
    }

    /**
     * 会话改名 / 归档请求体（不可变 record）：字段为 null 表示不改该项。
     *
     * @param name     新名称（1-100 字符，去首尾空白）
     * @param archived true 归档、false 取消归档
     */
    public record ConversationUpdateBo(String name, Boolean archived) {
    }
}
