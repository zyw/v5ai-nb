package xin.v5ai.nb.common.encrypt.apikey;

import cn.hutool.crypto.digest.BCrypt;
import xin.v5ai.nb.common.core.domain.model.Tuple2;

import java.security.SecureRandom;

public class BcryptAgentApiKeyVerifier implements AgentApiKeyVerifier {

    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public boolean verify(String apiKey, String secretHash) {
        return secretHash != null && BCrypt.checkpw(apiKey, secretHash);
    }

    @Override
    public Tuple2<String, String> generateApiKey() {
        var rawKey = genApiKey();
        return Tuple2.<String, String>builder()
                .first(rawKey)
                .second(BCrypt.hashpw(rawKey))
                .build();
    }

    private String genApiKey() {
        var sb = new StringBuilder("v5ai_");
        for (int i = 0; i < 32; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }
}
