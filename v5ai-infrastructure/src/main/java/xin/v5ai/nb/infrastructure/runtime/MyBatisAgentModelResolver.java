package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.AgentModelResolver;
import xin.v5ai.nb.common.encrypt.core.CredentialCipher;
import xin.v5ai.nb.model.mapper.ModelRuntimeConfigGateway;
import xin.v5ai.nb.model.core.AgentScopeModelFactory;

@Component
public class MyBatisAgentModelResolver implements AgentModelResolver {
    private final ModelRuntimeConfigGateway runtimeConfigGateway;
    private final CredentialCipher credentialCipher;
    private final AgentScopeModelFactory modelFactory;

    public MyBatisAgentModelResolver(
            ModelRuntimeConfigGateway runtimeConfigGateway,
            CredentialCipher credentialCipher,
            AgentScopeModelFactory modelFactory
    ) {
        this.runtimeConfigGateway = runtimeConfigGateway;
        this.credentialCipher = credentialCipher;
        this.modelFactory = modelFactory;
    }

    @Override
    public Model resolve(Long modelId) {
        var config = runtimeConfigGateway.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("model runtime config not found: " + modelId);
        }
        return modelFactory.create(config, credentialCipher.decrypt(config.credentialsCiphertext()));
    }
}
