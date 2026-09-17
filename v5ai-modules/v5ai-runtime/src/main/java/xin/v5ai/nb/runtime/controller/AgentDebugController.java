package xin.v5ai.nb.runtime.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.runtime.domain.bo.ChatRequest;

/**
 * 管理端 AgentDTO 调试接口：使用管理端 Sa-Token 鉴权（/api/admin/** 拦截），
 * 直接调用运行时执行对话，无需生成 API Key，供管理后台的预览/调试面板使用。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentKey}/chat")
public class AgentDebugController {

    private final AgentRuntime agentRuntime;

    /**
     * 必须显式限定 {@code agentDebugRuntime}：容器里同时存在 agentRuntime（仅已发布）
     * 与 agentDebugRuntime（允许草稿）两个同类型 Bean，缺省会注入到仅已发布那个，
     * 导致后台调试草稿 AgentDTO 时直接报 "agent is not published"。
     */
    public AgentDebugController(@Qualifier("agentDebugRuntime") AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatRequest request
    ) {
        return toSse(agentRuntime.stream(new AgentRunBo(agentKey, request.conversationId(), request.query())));
    }

    private static Flux<ServerSentEvent<RuntimeRunEventDTO>> toSse(Flux<RuntimeRunEventDTO> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name())
                .id(event.runId())
                .build());
    }
}
