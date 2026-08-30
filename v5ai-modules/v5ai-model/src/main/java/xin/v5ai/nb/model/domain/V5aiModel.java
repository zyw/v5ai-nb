package xin.v5ai.nb.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

/**
 * <p>
 * 模型配置实体（v5ai_model）：providerId/modelKey/modelType 为唯一组合，
 * 凭据加密存储；modelName 为展示名称，config 保存模型扩展参数（JSON，
 * 对应 {@link xin.v5ai.nb.model.core.config.ConfigExtAttrsDTO}），
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

    private Long providerId;

    private String modelKey;

    private String modelType;

    private String endpoint;

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
     * 模型参数配置（JSON 格式，对应 ConfigExtAttrsDTO）
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
