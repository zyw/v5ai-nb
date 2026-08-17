package xin.v5ai.nb.infrastructure.apiKey;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.apiKey.AgentApiKeyVerifier;

@Component
public class BcryptAgentApiKeyVerifier implements AgentApiKeyVerifier {
    private final AgentApiKeyStore store;
    private final PasswordEncoder passwordEncoder;

    public BcryptAgentApiKeyVerifier(AgentApiKeyStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean verify(String agentKey, String apiKey) {
        var secretHash = store.findSecretHash(agentKey);
        return secretHash != null && passwordEncoder.matches(apiKey, secretHash);
    }
}
