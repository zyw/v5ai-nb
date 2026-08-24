package xin.v5ai.nb.runtime.controller;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunResult;
import xin.v5ai.nb.common.agentscope.core.domain.RuntimeEvent;
import xin.v5ai.nb.common.agentscope.core.service.MessageService;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.runtime.controller.vo.ChatRequest;
import xin.v5ai.nb.runtime.controller.vo.ConversationMessageResponse;

@RestController
@RequestMapping("/api/v1/agents/{agentKey}/chat")
public class ChatController {
    private final AgentRuntime agentRuntime;
    private final MessageService messageService;

    public ChatController(AgentRuntime agentRuntime, MessageService messageService) {
        this.agentRuntime = agentRuntime;
        this.messageService = messageService;
    }

    @PostMapping("/stream")
    public Flux<ServerSentEvent<RuntimeEvent>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatRequest request
    ) {
        return toSse(agentRuntime.stream(new AgentRunRequest(agentKey, request.conversationId(), request.query())));
    }

    @PostMapping
    public Mono<AgentRunResult> call(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatRequest request
    ) {
        return agentRuntime.call(new AgentRunRequest(agentKey, request.conversationId(), request.query()));
    }

    @GetMapping("/conversations/{conversationId}")
    public R<PageResult<ConversationMessageResponse>> conversationMessages(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            PageQuery pageQuery
    ) {
        var list = messageService.findByConversationId(conversationId).stream()
                .map(ConversationMessageResponse::from)
                .toList();
//     TODO PageUtils   return R.ok(PageUtils.page(list, pageQuery));
        return R.ok();
    }

    @PostMapping("/conversations/{conversationId}/resume")
    public Flux<ServerSentEvent<RuntimeEvent>> resume(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ChatRequest request
    ) {
        var history = messageService.findByConversationId(conversationId);
        var resumeRequest = new AgentRunRequest(agentKey, conversationId, request.query()).withHistory(history);
        return toSse(agentRuntime.stream(resumeRequest));
    }

    private Flux<ServerSentEvent<RuntimeEvent>> toSse(Flux<RuntimeEvent> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name())
                .id(event.runId())
                .build());
    }
}
