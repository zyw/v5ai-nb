package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.encrypt.apikeys.BcryptAgentApiKeysVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(verifier.verify("v5ai_wrong-key", pair.getSecond()));
        // 无密文应失败
        assertFalse(verifier.verify(pair.getFirst(), null));
    }

    /**
     * tracking id 契约：明文 Key 内嵌的 UUID（36 位）就是 tracking id，运行时靠它在唯一索引上定位密钥行；
     * 前缀、总长或 UUID 形态不符时必须返回 null，避免用畸形输入去查库。
     */
    @Test
    void trackingIdIsExtractedFromGeneratedKey() {
        Tuple2<String, String> pair = verifier.generateApiKey();
        String plaintext = pair.getFirst();

        String trackingId = verifier.trackingIdOf(plaintext);
        // 明文 = v5ai_ + UUID(36) + 随机主体(32)
        assertEquals(41 + 32, plaintext.length());
        assertEquals(plaintext.substring(5, 41), trackingId);
        assertTrue(trackingId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertNull(verifier.trackingIdOf(null));
        assertNull(verifier.trackingIdOf(""));
        assertNull(verifier.trackingIdOf("sk-abcdefghijklmnopqrstuvwxyz012345"));
        assertNull(verifier.trackingIdOf("v5ai_short"));
        // 长度对但不是 UUID 也必须拒绝
        assertNull(verifier.trackingIdOf("v5ai_" + "z".repeat(36) + "a".repeat(32)));
    }
}
