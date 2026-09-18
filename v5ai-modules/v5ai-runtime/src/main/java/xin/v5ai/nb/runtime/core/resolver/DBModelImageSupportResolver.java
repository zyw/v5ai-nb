package xin.v5ai.nb.runtime.core.resolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;

/**
 * {@link ModelImageSupportResolver} 的数据库实现：按模型 ID 取运行时配置，
 * 判 {@code config.capabilities} 是否含 {@code image}。
 *
 * <p>模型配置读取失败（模型被删除、供应商不可用等）时按「不支持」返回并告警：
 * 附件是可选能力，能力判定失败不应让整个运行崩掉。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DBModelImageSupportResolver implements ModelImageSupportResolver {

    /**
     * 表示支持图片输入的 capability 取值（管理端与既有代码统一写 image，不是 vision）。
     */
    private static final String CAPABILITY_IMAGE = "image";

    private final ModelConfigRetrieve configRetrieve;

    @Override
    public boolean supportsImageInput(Long modelId) {
        if (modelId == null) {
            return false;
        }
        try {
            var runtimeConfig = configRetrieve.findRuntimeConfigByModelId(modelId);
            var config = runtimeConfig == null ? null : runtimeConfig.config();
            var capabilities = config == null ? null : config.getCapabilities();
            if (capabilities == null) {
                return false;
            }
            return capabilities.stream()
                    .anyMatch(capability -> capability != null && CAPABILITY_IMAGE.equalsIgnoreCase(capability.trim()));
        } catch (Exception exception) {
            log.warn("failed to resolve image capability for model {}: {}", modelId, exception.getMessage());
            return false;
        }
    }
}
