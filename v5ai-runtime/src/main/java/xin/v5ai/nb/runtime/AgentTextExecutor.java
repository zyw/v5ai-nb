package xin.v5ai.nb.runtime;

import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;

/**
 * 文本型 Agent 执行器：把应用配置 + 运行请求转化为文本事件流。
 *
 * 与 {@link AgentRuntime} 的关系：Runtime 负责上层编排（解析应用、
 * RAG、事件映射），具体"怎么驱动模型跑"交给本接口的实现。
 * 只含一个抽象方法，可用 Lambda 或方法引用实现。
 */
@FunctionalInterface
public interface AgentTextExecutor {
    /**
     * 流式执行 Agent，产出文本事件流。
     *
     * @param application 已发布的应用配置（模型、名称等）
     * @param request     本次运行的请求（含 query 与可选 RAG 上下文）
     * @return 文本事件流（Text 增量 / ModelCall 标记）
     */
    Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request);
}
