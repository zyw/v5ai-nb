package xin.v5ai.nb.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

/**
 * <p>
 * 模型配置实体（v5ai_model）：id 是平台内模型配置身份，providerId/modelKey
 * 可以重复以支持同一上游模型在多个账户、服务器或 Base URL 下的配置，
 * 凭据加密存储；modelName 为展示名称，config 保存模型扩展参数（JSON，
 * 对应 {@link ModelExtConfigAttrs}），
 * scope 区分全局/个人模型，ownerId 为个人模型的所有者。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_model")
public class V5aiModel extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提供商 ID
     */
    private Long providerId;

    /**
     * 模型密钥
     */
    private String modelKey;

    /**
     * 模型类型
     */
    private String modelType;

    /**
     * API 基础地址（与凭据 JSON 的 baseUrl 键一致）
     */
    private String baseUrl;

    /**
     * 凭据加密存储
     */
    private String credentialsCiphertext;

    /**
     * 是否启用
     */
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
}
