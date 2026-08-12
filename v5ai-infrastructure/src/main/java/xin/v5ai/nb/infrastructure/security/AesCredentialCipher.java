package xin.v5ai.nb.infrastructure.security;

import xin.v5ai.nb.model.CredentialCipher;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AesCredentialCipher implements CredentialCipher {
    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
    }
}
