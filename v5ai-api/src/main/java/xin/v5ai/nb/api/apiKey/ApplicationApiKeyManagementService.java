package xin.v5ai.nb.api.apiKey;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.infrastructure.apiKey.ApplicationApiKeyStore;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationApiKeyManagementService {
    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApplicationApiKeyStore store;
    private final PasswordEncoder passwordEncoder;

    public ApplicationApiKeyManagementService(ApplicationApiKeyStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    public CreateApiKeyResult createApiKey(String appKey) {
        var rawKey = generateApiKey();
        store.upsertSecret(appKey, passwordEncoder.encode(rawKey));
        return new CreateApiKeyResult(appKey, rawKey);
    }

    public List<ApplicationApiKeyStore.ApiKeyRecord> listApiKeys() {
        return store.list();
    }

    public void deleteApiKey(String appKey) {
        store.delete(appKey);
    }

    private String generateApiKey() {
        var sb = new StringBuilder("v5ai_");
        for (int i = 0; i < 32; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }

    public record CreateApiKeyResult(String appKey, String apiKey) {
    }
}
