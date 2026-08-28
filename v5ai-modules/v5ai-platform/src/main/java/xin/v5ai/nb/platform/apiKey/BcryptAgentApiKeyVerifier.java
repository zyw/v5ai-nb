package xin.v5ai.nb.platform.apiKey;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.apiKey.AgentApiKeyVerifier;
import xin.v5ai.nb.platform.service.IV5aiApiKeyService;

@Component
@RequiredArgsConstructor
public class BcryptAgentApiKeyVerifier implements AgentApiKeyVerifier {

    private final IV5aiApiKeyService service;;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean verify(String agentKey, String apiKey) {
        var secretHash = service.findSecretHash(agentKey);
        return secretHash != null && passwordEncoder.matches(apiKey, secretHash);
    }
}
