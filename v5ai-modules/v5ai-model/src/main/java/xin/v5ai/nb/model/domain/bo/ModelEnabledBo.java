package xin.v5ai.nb.model.domain.bo;

/**
 * 模型启用/停用切换请求体。
 *
 * @param enabled 是否启用（true 启用，false 停用；缺省视为停用，调用方必须显式提交）
 */
public record ModelEnabledBo(Boolean enabled) {
}
