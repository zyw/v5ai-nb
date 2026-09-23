package xin.v5ai.nb.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.bo.ChatBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;

/**
 * 管理端 AgentDTO 调试接口：使用管理端 Sa-Token 鉴权（/api/admin/** 拦截），
 * 直接调用运行时执行对话，无需生成 API Key，供管理后台的预览/调试面板使用。
 *
 * <p>入参与线上 {@code /api/v1/agents/{agentKey}/chat/stream} 同形（{@link ChatBo}），
 * 包括图片附件与 MCP/Skill 收窄项——调试面板要能复现线上行为，附件不能在这里被悄悄丢掉。</p>
 */
@RestController
@RequestMapping("/api/admin/agents/{agentKey}/chat")
public class AgentDebugController {

    private final AgentRuntime agentRuntime;
    private final PublishedAgentResolver publishedAgentResolver;
    private final ModelImageSupportResolver modelImageSupportResolver;

    /**
     * 必须显式限定 {@code agentDebugRuntime}：容器里同时存在 agentRuntime（仅已发布）
     * 与 agentDebugRuntime（允许草稿）两个同类型 Bean，缺省会注入到仅已发布那个，
     * 导致后台调试草稿 AgentDTO 时直接报 "agent is not published"。
     */
    public AgentDebugController(@Qualifier("agentDebugRuntime") AgentRuntime agentRuntime,
                                PublishedAgentResolver publishedAgentResolver,
                                ModelImageSupportResolver modelImageSupportResolver) {
        this.agentRuntime = agentRuntime;
        this.publishedAgentResolver = publishedAgentResolver;
        this.modelImageSupportResolver = modelImageSupportResolver;
    }

    @SaCheckPermission(value = {"monitor:debug:chat", "agent:agent:edit"}, mode = SaMode.OR)
    @PostMapping(value = "/stream", produces = "text/event-stream")
    public Flux<ServerSentEvent<RuntimeRunEventDTO>> stream(
            @PathVariable("agentKey") String agentKey,
            @RequestBody ChatBo bo
    ) {
        assertAttachmentsSupported(agentKey, bo);
        return toSse(agentRuntime.stream(new AgentRunBo(agentKey, bo.conversationId(), bo.query())
                .withWebSearch(bo.webSearch())
                .withAttachments(bo.attachments())
                .withDisabledMcpServerIds(bo.disabledMcpServerIds())
                .withDisabledSkillIds(bo.disabledSkillIds())));
    }

    /**
     * 附件门控：与线上门户同一条规则——所绑 CHAT 模型未声明 image 能力时直接 400，
     * 而不是把图片丢掉或让它以「模型报错」的形式失败（见 CONTEXT.md「支持图片输入」）。
     *
     * <p>这里按**草稿态**解析（调试面板本就是在跑未发布的配置），因此用 {@code resolveForDebug}。</p>
     */
    private void assertAttachmentsSupported(String agentKey, ChatBo bo) {
        if (bo.attachments().isEmpty()) {
            return;
        }
        var agent = publishedAgentResolver.resolveForDebug(agentKey);
        if (!modelImageSupportResolver.supportsImageInput(agent.modelId())) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "当前 Agent 绑定的模型不支持图片输入");
        }
    }

    private static Flux<ServerSentEvent<RuntimeRunEventDTO>> toSse(Flux<RuntimeRunEventDTO> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name())
                .id(event.runId())
                .build());
    }
}
