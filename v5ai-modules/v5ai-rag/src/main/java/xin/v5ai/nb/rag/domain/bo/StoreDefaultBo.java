package xin.v5ai.nb.rag.domain.bo;

/**
 * 存储实例默认切换请求体。
 *
 * @param isDefault 是否设为该分类默认实例（true 设置默认，false 取消默认）
 */
public record StoreDefaultBo(Boolean isDefault) {
}
