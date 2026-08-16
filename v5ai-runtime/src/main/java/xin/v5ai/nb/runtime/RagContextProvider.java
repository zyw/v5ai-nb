package xin.v5ai.nb.runtime;

import reactor.core.publisher.Mono;
import xin.v5ai.nb.agent.Agent;

/**
 * RAG 上下文构建器：为一次运行请求生成检索上下文（知识库引用）。
 *
 * 实现约定：当应用未绑定知识库或未配置检索时，可返回空字符串，
 * 运行时将直接跳过 RAG，正常执行 Agent。
 */
public interface RagContextProvider {
    /**
     * 异步构建检索上下文。
     *
     * @param application 已发布的应用配置（用于确定绑定的知识库等）
     * @param request     本次运行请求（可从中取用户提问作为检索 query）
     * @return 检索到的上下文文本；无知识/无检索时返回空字符串
     */
    Mono<String> buildContext(Agent application, AgentRunRequest request);
}
