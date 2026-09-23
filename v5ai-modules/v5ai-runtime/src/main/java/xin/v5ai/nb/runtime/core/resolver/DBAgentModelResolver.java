package xin.v5ai.nb.runtime.core.resolver;

import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

@Component
@RequiredArgsConstructor
public class DBAgentModelResolver implements AgentModelResolver {

    private final ModelConfigRetrieve configRetrieve;
    private final CredentialCipher credentialCipher;
    private final AgentScopeModelFactory modelFactory;

    @Override
    public Model resolve(Long modelId) {
        var config = configRetrieve.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("model runtime config not found: " + modelId);
        }
        return modelFactory.create(config, credentialCipher.decrypt(config.credentialsCiphertext()));
    }
}
