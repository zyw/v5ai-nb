package xin.v5ai.nb.model.domain.bo;

/**
 * 模型默认标记切换请求体。
 *
 * @param isDefault 是否设为同类型默认模型（true 设置默认，false 取消默认；缺省视为取消）
 */
public record ModelDefaultBo(Boolean isDefault) {
}
