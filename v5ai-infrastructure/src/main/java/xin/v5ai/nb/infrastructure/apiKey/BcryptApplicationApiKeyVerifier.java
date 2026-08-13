package xin.v5ai.nb.infrastructure.apiKey;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.apiKey.ApplicationApiKeyVerifier;

@Component
public class BcryptApplicationApiKeyVerifier implements ApplicationApiKeyVerifier {
    private final ApplicationApiKeyStore store;
    private final PasswordEncoder passwordEncoder;

    public BcryptApplicationApiKeyVerifier(ApplicationApiKeyStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean verify(String appKey, String apiKey) {
        var secretHash = store.findSecretHash(appKey);
        return secretHash != null && passwordEncoder.matches(apiKey, secretHash);
    }
}
