package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.encrypt.apikeys.BcryptAgentApiKeysVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent API Key 校验参数顺序回归测试：
 * 接口契约 verify(apiKey, secretHash)——第一参为请求携带的明文，第二参为库中 BCrypt 密文。
 */
class BcryptAgentApiKeysVerifierTest {

    private final BcryptAgentApiKeysVerifier verifier = new BcryptAgentApiKeysVerifier();

    @Test
    void verifyMatchesGeneratedKey() {
        Tuple2<String, String> pair = verifier.generateApiKey();

        // 正确的参数顺序：明文在前，密文在后
        assertTrue(verifier.verify(pair.getFirst(), pair.getSecond()));
        // 参数顺序颠倒应失败（防止再次踩坑）
        assertFalse(verifier.verify(pair.getSecond(), pair.getFirst()));
        // 错误明文应失败
        assertFalse(verifier.verify("v5ai-wrong-key", pair.getSecond()));
        // 无密文应失败
        assertFalse(verifier.verify(pair.getFirst(), null));
    }

    /**
     * keyHash 契约：明文 Key 的 SHA-256 摘要（64 位小写十六进制）是运行时唯一定位行的依据；
     * 摘要稳定、不同 Key 摘要不同，前缀/长度不符时返回 null（不用畸形输入去查库）。
     */
    @Test
    void keyHashIsStableSha256OfPlaintext() {
        Tuple2<String, String> pair = verifier.generateApiKey();
        String plaintext = pair.getFirst();

        // 明文 = v5ai- + 32 位大小写字母与数字，不含任何跟踪信息
        assertEquals(5 + 32, plaintext.length());
        assertTrue(plaintext.matches("v5ai-[A-Za-z0-9]{32}"));

        String hash = verifier.keyHash(plaintext);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, verifier.keyHash(plaintext));
        assertNotEquals(hash, verifier.keyHash(verifier.generateApiKey().getFirst()));

        assertNull(verifier.keyHash(null));
        assertNull(verifier.keyHash(""));
        assertNull(verifier.keyHash("sk-abcdefghijklmnopqrstuvwxyz012345"));
        assertNull(verifier.keyHash("v5ai-short"));
        assertNull(verifier.keyHash(plaintext + "x"));
    }
}
