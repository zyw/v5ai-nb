package xin.v5ai.nb.runtime.core.resolver;

import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelRuntimeConfigGateway;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;
import xin.v5ai.nb.common.encrypt.core.CredentialCipher;

@Component
@RequiredArgsConstructor
public class MyBatisAgentModelResolver implements AgentModelResolver {

    private final ModelRuntimeConfigGateway runtimeConfigGateway;
    private final CredentialCipher credentialCipher;
    private final AgentScopeModelFactory modelFactory;

    @Override
    public Model resolve(Long modelId) {
        var config = runtimeConfigGateway.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("model runtime config not found: " + modelId);
        }
        return modelFactory.create(config, credentialCipher.decrypt(config.credentialsCiphertext()));
    }
}
