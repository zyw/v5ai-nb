package xin.v5ai.nb.common.encrypt.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AesCredentialCipher implements CredentialCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;

    public AesCredentialCipher() {
        this("0123456789abcdef0123456789abcdef");
    }

    public AesCredentialCipher(String key) {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("credential cipher key must be 32 bytes for AES-256");
        }
        this.key = keyBytes;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            var iv = new byte[IV_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var payload = ByteBuffer.allocate(1 + iv.length + encrypted.length)
                    .put((byte) 1)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("failed to encrypt model credentials", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            var payload = Base64.getDecoder().decode(ciphertext);
            var buffer = ByteBuffer.wrap(payload);
            byte version = buffer.get();
            if (version != 1) {
                throw new IllegalArgumentException("unsupported credential ciphertext version");
            }
            var iv = new byte[IV_BYTES];
            buffer.get(iv);
            var encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("failed to decrypt model credentials", e);
        }
    }
}
