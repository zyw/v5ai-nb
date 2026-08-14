package xin.v5ai.nb.api.runtime;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.AgentRunResult;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.MessageRepository;
import xin.v5ai.nb.runtime.RuntimeEvent;

import java.util.List;

@RestController
@RequestMapping("/api/v1/apps/{appKey}/chat")
public class ChatController {
    private final AgentRuntime agentRuntime;
    private final MessageRepository messageRepository;

    public ChatController(AgentRuntime agentRuntime, MessageRepository messageRepository) {
        this.agentRuntime = agentRuntime;
        this.messageRepository = messageRepository;
    }

    @PostMapping("/stream")
    public Flux<ServerSentEvent<RuntimeEvent>> stream(
            @PathVariable("appKey") String appKey,
            @RequestBody ChatRequest request
    ) {
        return toSse(agentRuntime.stream(new AgentRunRequest(appKey, request.conversationId(), request.query())));
    }

    @PostMapping
    public Mono<AgentRunResult> call(
            @PathVariable("appKey") String appKey,
            @RequestBody ChatRequest request
    ) {
        return agentRuntime.call(new AgentRunRequest(appKey, request.conversationId(), request.query()));
    }

    @GetMapping("/conversations/{conversationId}")
    public List<ConversationMessageResponse> conversationMessages(
            @PathVariable("appKey") String appKey,
            @PathVariable("conversationId") String conversationId
    ) {
        return messageRepository.findByConversationId(conversationId).stream()
                .map(ConversationMessageResponse::from)
                .toList();
    }

    @PostMapping("/conversations/{conversationId}/resume")
    public Flux<ServerSentEvent<RuntimeEvent>> resume(
            @PathVariable("appKey") String appKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ChatRequest request
    ) {
        var history = messageRepository.findByConversationId(conversationId);
        var resumeRequest = new AgentRunRequest(appKey, conversationId, request.query()).withHistory(history);
        return toSse(agentRuntime.stream(resumeRequest));
    }

    private Flux<ServerSentEvent<RuntimeEvent>> toSse(Flux<RuntimeEvent> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name())
                .id(event.runId())
                .build());
    }
}
