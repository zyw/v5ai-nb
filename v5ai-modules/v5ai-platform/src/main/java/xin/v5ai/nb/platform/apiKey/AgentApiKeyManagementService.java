package xin.v5ai.nb.platform.apiKey;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.platform.apiKey.AgentApiKeyStore;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentApiKeyManagementService {
    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentApiKeyStore store;
    private final PasswordEncoder passwordEncoder;

    public AgentApiKeyManagementService(AgentApiKeyStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    public CreateApiKeyResult createApiKey(String agentKey) {
        var rawKey = generateApiKey();
        store.upsertSecret(agentKey, passwordEncoder.encode(rawKey));
        return new CreateApiKeyResult(agentKey, rawKey);
    }

    public List<AgentApiKeyStore.ApiKeyRecord> listApiKeys() {
        return store.list();
    }

    public void deleteApiKey(String agentKey) {
        store.delete(agentKey);
    }

    private String generateApiKey() {
        var sb = new StringBuilder("v5ai_");
        for (int i = 0; i < 32; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }

    public record CreateApiKeyResult(String agentKey, String apiKey) {
    }
}
