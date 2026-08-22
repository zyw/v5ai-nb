package xin.v5ai.nb.api.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.RuntimeEvent;

/**
 * 管理端 Agent 调试接口：使用管理端 Sa-Token 鉴权（/api/admin/** 拦截），
 * 直接调用运行时执行对话，无需生成 API Key，供管理后台的预览/调试面板使用。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentKey}/chat")
public class AgentDebugController {
    private final AgentRuntime agentRuntime;

    public AgentDebugController(@Qualifier("agentDebugRuntime") AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public Flux<ServerSentEvent<RuntimeEvent>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatRequest request
    ) {
        return toSse(agentRuntime.stream(new AgentRunRequest(agentKey, request.conversationId(), request.query())));
    }

    private static Flux<ServerSentEvent<RuntimeEvent>> toSse(Flux<RuntimeEvent> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name())
                .id(event.runId())
                .build());
    }
}
