package xin.v5ai.nb.common.agentscope.core.resolver;

/**
 * 模型「支持图片输入」能力判定：读 {@code v5ai_model.config} 里的
 * {@code capabilities} 数组，含 {@code image} 即视为支持（见 CONTEXT.md「支持图片输入」）。
 *
 * <p>它是附件能力的唯一开关：Agent 所绑 CHAT 模型未声明该项时，门户禁用上传入口
 * （bootstrap 的 {@code imageSupported}）、运行接口拒绝携带附件的请求（400）。</p>
 *
 * <p>未装配实现时一律视为不支持——这类模型的报错会指向"模型不支持图片"，
 * 比悄悄把图片丢掉更容易定位。</p>
 */
public interface ModelImageSupportResolver {

    /**
     * 该模型是否声明支持图片输入。
     *
     * @param modelId 模型 ID；为 {@code null} 时返回 false
     * @return 支持返回 true
     */
    boolean supportsImageInput(Long modelId);
}
