package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.infrastructure.model.ModelRuntimeConfigGateway;
import xin.v5ai.nb.model.CredentialCipher;
import xin.v5ai.nb.model.ModelConnectionTester;

import java.time.Duration;
import java.util.List;

@Component
public class AgentScopeModelConnectionTester implements ModelConnectionTester {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ModelRuntimeConfigGateway runtimeConfigGateway;
    private final CredentialCipher credentialCipher;
    private final AgentScopeModelFactory modelFactory;

    public AgentScopeModelConnectionTester(
            ModelRuntimeConfigGateway runtimeConfigGateway,
            CredentialCipher credentialCipher,
            AgentScopeModelFactory modelFactory
    ) {
        this.runtimeConfigGateway = runtimeConfigGateway;
        this.credentialCipher = credentialCipher;
        this.modelFactory = modelFactory;
    }

    @Override
    public boolean test(Long modelId) {
        var config = runtimeConfigGateway.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("model runtime config not found: " + modelId);
        }
        var model = modelFactory.create(config, credentialCipher.decrypt(config.credentialsCiphertext()));
        var ping = Msg.builder().role(MsgRole.USER).textContent("ping").build();
        try {
            var first = model.stream(
                            List.of(ping),
                            List.of(),
                            GenerateOptions.builder().maxTokens(5).build())
                    .blockFirst(TIMEOUT);
            return first != null;
        } catch (Exception exception) {
            return false;
        }
    }
}
