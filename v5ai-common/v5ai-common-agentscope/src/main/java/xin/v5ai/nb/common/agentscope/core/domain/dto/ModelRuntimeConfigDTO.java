package xin.v5ai.nb.common.agentscope.core.domain.dto;

/**
 * 模型运行时配置 DTO（领域契约）：由模型与供应商实体拼装，供运行时模型工厂构建模型与解密凭据。
 *
 * @param modelId               模型 ID
 * @param modelKey              模型标识（模型名，如 gpt-4o）
 * @param modelType             模型类型（LLM / EMBEDDING 等）
 * @param providerId            模型供应商 ID
 * @param providerKey           供应商对外标识
 * @param providerType          供应商类型（如 openai / dashscope）
 * @param credentialsCiphertext 模型凭据密文（AES-GCM 加密，使用前需解密）
 */
public record ModelRuntimeConfigDTO(
        Long modelId,
        String modelKey,
        String modelType,
        Long providerId,
        String providerKey,
        String providerType,
        String credentialsCiphertext
) {
}
