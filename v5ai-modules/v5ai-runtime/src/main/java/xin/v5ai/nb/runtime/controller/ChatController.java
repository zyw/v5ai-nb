package xin.v5ai.nb.runtime.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.bo.ChatBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.runtime.core.service.MessageService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agents/{agentKey}/chat")
public class ChatController {

    private final AgentRuntime agentRuntime;
    private final MessageService messageService;

    @PostMapping("/stream")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatBo bo
    ) {
        return toSse(agentRuntime.stream(new AgentRunBo(agentKey, bo.conversationId(), bo.query())));
    }

    @PostMapping
    public Mono<AgentRunVo> call(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatBo bo
    ) {
        return agentRuntime.call(new AgentRunBo(agentKey, bo.conversationId(), bo.query()));
    }

    @GetMapping("/conversations/{conversationId}")
    public R<List<ConversationMessageResult>> conversationMessages(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId
    ) {
        var list = messageService.findByConversationId(conversationId).stream()
                .map(ConversationMessageResult::from)
                .toList();
        log.info("Found {} messages for conversationId: {}", list.size(), conversationId);
        return R.ok(list);
    }

    @PostMapping("/conversations/{conversationId}/resume")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> resume(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ChatBo bo
    ) {
        var history = messageService.findByConversationId(conversationId);
        var resumeRequest = new AgentRunBo(agentKey, conversationId, bo.query()).withHistory(history);
        return toSse(agentRuntime.stream(resumeRequest));
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
     * @param role    消息角色（USER/ASSISTANT）
     * @param content 消息内容
     */
    public record ConversationMessageResult(MessageRole role, String content) {
        /**
         * 将领域消息 {@link SessionMessage} 转为 API 响应体（去掉会话/应用等内部字段）。
         */
        public static ConversationMessageResult from(SessionMessage message) {
            return new ConversationMessageResult(message.role(), message.content());
        }
    }
}
