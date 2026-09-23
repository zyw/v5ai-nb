package xin.v5ai.nb.common.encrypt.cipher;

/**
 * 凭据加密端口：对敏感配置（模型 API Key、MCP Headers/环境变量、第三方 Token 等）做加解密。
 *
 * 实现约定：使用对称加密（本项目为 AES-GCM），密文以非明文形式落库。
 * {@code decrypt} 提供默认实现（抛异常），单次写场景（只加密不需要解密）可不实现。
 */
public interface CredentialCipher {
    /**
     * 加密明文凭据。
     *
     * @param plaintext 明文（API Key、Token 等）
     * @return 加密后的密文
     */
    String encrypt(String plaintext);

    /**
     * 解密密文凭据。
     *
     * @param ciphertext 密文
     * @return 还原的明文
     * @throws UnsupportedOperationException 当实现不支持解密时（默认行为）
     */
    default String decrypt(String ciphertext) {
        throw new UnsupportedOperationException("credential decrypt is not implemented");
    }
}
