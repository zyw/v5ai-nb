package xin.v5ai.nb.model.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.common.core.enums.ModelType;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.model.domain.V5aiModel;

/**
 * 模型配置请求体。
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Data
@AutoMapper(target = V5aiModel.class, reverseConvertGenerate = false)
public class ModelBo {

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 供应商标识（providerKey），创建时必填，用于解析 providerId
     */
    @NotBlank(message = "供应商标识不能为空")
    private String providerKey;

    /**
     * 模型标识
     */
    @NotBlank(message = "模型标识不能为空")
    private String modelKey;

    /**
     * 模型名称（创建时必填，为空时回退为模型标识）
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
     * 模型作用域（GLOBAL=全局 / PERSONAL=个人），缺省 GLOBAL
     */
    private String scope;

    /**
     * 是否为默认模型，缺省 false
     */
    private Boolean isDefault;

    /**
     * 所有者 ID（NULL=全局，具体值=用户 ID）
     */
    private Long ownerId;

    /**
     * 模型类型（CHAT / EMBEDDING / RERANK）
     */
    @NotNull(message = "模型类型不能为空")
    private ModelType type;

    /**
     * 可选的自定义端点
     */
    private String endpoint;

    /**
     * 是否启用，默认 true
     */
    private Boolean enabled;

    /**
     * 明文凭据 JSON（apiKey/baseUrl/temperature/maxTokens），仅创建或改密时提交
     */
    private String credentials;
}
