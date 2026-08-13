package xin.v5ai.nb.runtime;

import io.agentscope.core.model.Model;

@FunctionalInterface
public interface AgentModelResolver {
    Model resolve(Long modelId);
}
