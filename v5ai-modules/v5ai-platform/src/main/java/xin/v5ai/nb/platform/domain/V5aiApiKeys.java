package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.time.OffsetDateTime;

/**
 * API Key（v5ai_api_keys）：归属创建用户，可访问的 Agent 由 {@link V5aiApiKeysAgent} 绑定。
 *
 * <p>明文 Key 形如 {@code v5ai-<32 位大小写字母与数字>}，仅创建时返回一次；库内只存摘要与密文：
 * {@code key_hash}（sha256，运行时唯一定位行）与 {@code secret_hash}（BCrypt，二次校验）。
 * {@code tracking_id} 是与明文无关的独立 UUID，只用于展示与日志/审计对账。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_api_keys")
public class V5aiApiKeys extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属用户（plm_user.id）
     */
    private Long userId;

    /**
     * Key 名称（页面主标识）
     */
    private String name;

    /**
     * 跟踪 ID：独立生成的 UUID，与明文 Key 无字符关系，仅供展示与日志/审计对账
     */
    private String trackingId;

    /**
     * 明文 Key 的 SHA-256 摘要（小写十六进制），运行时唯一定位密钥行
     */
    @TableField("key_hash")
    private String keyHash;

    /**
     * API Key 密文（BCrypt）
     */
    @TableField("secret_hash")
    private String secretHash;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 最新使用时间（鉴权成功时刷新，不代表业务数据被修改，故不改 updated_at）
     */
    private OffsetDateTime lastUsedAt;
}