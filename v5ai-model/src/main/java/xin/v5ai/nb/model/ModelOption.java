package xin.v5ai.nb.model;

/**
 * 模型下拉选项的轻量视图（避免为下拉框全量加载凭据密文等重字段）。
 *
 * @param id          主键
 * @param providerKey 供应商标识
 * @param modelKey    模型标识
 * @param type        模型类型
 */
public record ModelOption(Long id, String providerKey, String modelKey, ModelType type) {
}
