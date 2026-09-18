package xin.v5ai.nb.runtime.controller;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
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
import xin.v5ai.nb.runtime.core.RunCancellationRegistry;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
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
     * 会话名称长度上限：与 {@code v5ai_conversation.name VARCHAR(100)} 一致。
     */
    private static final int MAX_CONVERSATION_NAME_LENGTH = 100;

    /**
     * 未命名会话在列表里的预览长度。
     */
    private static final int PREVIEW_MAX_LENGTH = 100;

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

    @PostMapping("/{agentKey}/chat/stream")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatBo bo,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        assertConversationIdValid(bo);
        assertAttachmentsSupported(agentKey, bo);
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
        assertAttachmentsSupported(agentKey, bo);
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

    @PostMapping("/{agentKey}/chat/conversations/{conversationId}/resume")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> resume(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ChatBo bo,
            HttpServletRequest request
    ) {
        var auth = requireAuth(request);
        assertAttachmentsSupported(agentKey, bo);
        var history = messageService.findByConversationId(conversationId);
        var resumeRequest = toRunBo(agentKey, bo, auth)
                .withConversationId(conversationId)
                .withHistory(history);
        return toSse(agentRuntime.stream(resumeRequest));
    }

    /**
     * 会话列表：限「本 Key + 本 Agent」，按最近活跃倒序。
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
        var conversations = conversationService.list(agentKey, auth.apiKeyId(), archived);
        var previews = messageService.findFirstUserMessageByConversationIds(
                conversations.stream().map(ConversationDTO::conversationId).toList());
        return R.ok(conversations.stream()
                .map(conversation -> ConversationSummaryResult.from(
                        conversation, previews.get(conversation.conversationId())))
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
    private void assertAttachmentsSupported(String agentKey, ChatBo bo) {
        if (bo.attachments().isEmpty()) {
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
     * @param content     消息内容
     * @param attachments 消息携带的附件（图片）
     */
    public record ConversationMessageResult(MessageRole role, String content,
                                            List<MessageAttachmentResult> attachments) {
        /**
         * 将领域消息 {@link SessionMessage} 转为 API 响应体（去掉会话等内部字段）。
         */
        public static ConversationMessageResult from(SessionMessage message) {
            return new ConversationMessageResult(message.role(), message.content(),
                    message.attachments().stream().map(MessageAttachmentResult::from).toList());
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
     * @param name           会话名称；未命名为 null
     * @param agentKey       Agent 标识
     * @param userId         归属用户（展示冗余）
     * @param archived       是否已归档
     * @param createdAt      创建时间
     * @param updatedAt      最近活跃时间
     * @param preview        未命名会话的展示兜底：首条用户消息的前若干字
     */
    public record ConversationSummaryResult(String conversationId, String name, String agentKey, Long userId,
                                            boolean archived, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                            String preview) {
        static ConversationSummaryResult from(ConversationDTO conversation, String preview) {
            return new ConversationSummaryResult(conversation.conversationId(), conversation.name(),
                    conversation.agentKey(), conversation.userId(), conversation.archived(),
                    conversation.createdAt(), conversation.updatedAt(),
                    StrUtil.maxLength(preview, PREVIEW_MAX_LENGTH));
        }
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
