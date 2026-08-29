package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.encrypt.apikey.BcryptAgentApiKeyVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent API Key 校验参数顺序回归测试：
 * 接口契约 verify(apiKey, secretHash)——第一参为请求携带的明文，第二参为库中 BCrypt 密文。
 */
class BcryptAgentApiKeyVerifierTest {

    private final BcryptAgentApiKeyVerifier verifier = new BcryptAgentApiKeyVerifier();

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
}
