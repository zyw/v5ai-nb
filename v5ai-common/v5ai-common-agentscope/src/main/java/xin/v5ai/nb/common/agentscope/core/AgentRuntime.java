package xin.v5ai.nb.common.agentscope.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;

/**
 * AgentDTO 运行时接口：定义两种执行方式，均为响应式（Reactor）。
 */
public interface AgentRuntime {
    /**
     * 流式执行：以事件流形式逐个推送 {@link RuntimeRunEventDTO}
     * （如思考过程、工具调用、增量回答等），适合流式输出场景。
     *
     * @param request 运行请求
     * @return 运行时事件流
     */
    Flux<RuntimeRunEventDTO> stream(AgentRunBo request);

    /**
     * 一次性执行：直到 AgentDTO 跑完整轮流程后才返回最终结果。
     *
     * @param request 运行请求
     * @return 最终运行结果（含 runId 与最终回答）
     */
    Mono<AgentRunVo> call(AgentRunBo request);
}
