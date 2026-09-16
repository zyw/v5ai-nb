package xin.v5ai.nb.common.agentscope.core.resolver;

/**
 * 模型工具调用能力判定：智能调用（{@code rag_call_mode=SMART}）依赖模型支持 function calling，
 * 当模型不支持时运行时回退为强制调用（{@code FORCED}）并告警。
 *
 * 未装配该 Bean 时默认视为「支持工具」；接入非工具调用模型时再提供读取
 * {@code ModelExtConfigAttrs.capabilities} 的实现。
 */
@FunctionalInterface
public interface ModelToolSupportResolver {
    boolean supportsToolCall(long modelId);
}