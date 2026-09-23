package xin.v5ai.nb.model.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.model.domain.V5aiModel;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * 模型配置视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Data
@AutoMapper(target = V5aiModel.class)
public class V5aiModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    private String modelKey;

    private String modelType;

    private String baseUrl;

    private String credentialsCiphertext;

    private Boolean enabled;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型描述
     */
    private String description;

    /**
     * 底层协议适配器标识（openai-compatible/http 等）
     */
    private String adapterKey;

    /**
     * 模型参数配置（JSON 格式，对应 ModelExtConfigAttrs）
     */
    private String config;

    /**
     * 模型作用域（GLOBAL=全局 / PERSONAL=个人）
     */
    private String scope;

    /**
     * 是否为默认模型
     */
    private Boolean isDefault;

    /**
     * 所有者 ID（NULL=全局，具体值=用户 ID）
     */
    private Long ownerId;

    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
