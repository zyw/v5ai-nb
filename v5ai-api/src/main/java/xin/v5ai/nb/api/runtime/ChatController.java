package xin.v5ai.nb.api.runtime;

import org.springframework.http.codec.ServerSentEvent;
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
import xin.v5ai.nb.runtime.RuntimeEvent;

@RestController
@RequestMapping("/api/v1/apps/{appKey}/chat")
public class ChatController {
    private final AgentRuntime agentRuntime;

    public ChatController(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @PostMapping("/stream")
    public Flux<ServerSentEvent<RuntimeEvent>> stream(
            @PathVariable("appKey") String appKey,
            @RequestBody ChatRequest request
    ) {
        return agentRuntime.stream(new AgentRunRequest(appKey, request.conversationId(), request.query()))
                .map(event -> ServerSentEvent.builder(event)
                        .event(event.type().name())
                        .id(event.runId())
                        .build());
    }

    @PostMapping
    public Mono<AgentRunResult> call(
            @PathVariable("appKey") String appKey,
            @RequestBody ChatRequest request
    ) {
        return agentRuntime.call(new AgentRunRequest(appKey, request.conversationId(), request.query()));
    }
}
