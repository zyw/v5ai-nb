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
@RequestMapping("/api/v1/agents/{agentKey}/chat")
public class ChatController {
    private final AgentRuntime agentRuntime;
    private final MessageRepository messageRepository;

    public ChatController(AgentRuntime agentRuntime, MessageRepository messageRepository) {
        this.agentRuntime = agentRuntime;
        this.messageRepository = messageRepository;
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
    public List<ConversationMessageResponse> conversationMessages(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId
    ) {
        return messageRepository.findByConversationId(conversationId).stream()
                .map(ConversationMessageResponse::from)
                .toList();
    }

    @PostMapping("/conversations/{conversationId}/resume")
    public Flux<ServerSentEvent<RuntimeEvent>> resume(
            @PathVariable("agentKey") String agentKey,
            @PathVariable("conversationId") String conversationId,
            @RequestBody ChatRequest request
    ) {
        var history = messageRepository.findByConversationId(conversationId);
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
