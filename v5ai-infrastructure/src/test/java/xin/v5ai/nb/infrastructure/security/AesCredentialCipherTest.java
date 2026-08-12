package xin.v5ai.nb.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesCredentialCipherTest {
    @Test
    void encryptUsesRandomizedAesInsteadOfBase64Encoding() {
        var cipher = new AesCredentialCipher("0123456789abcdef0123456789abcdef");
        var plaintext = "secret-key";

        var first = cipher.encrypt(plaintext);
        var second = cipher.encrypt(plaintext);

        assertThat(first).isNotEqualTo(plaintext);
        assertThat(first).isNotEqualTo(Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8)));
        assertThat(second).isNotEqualTo(first);
    }
}
