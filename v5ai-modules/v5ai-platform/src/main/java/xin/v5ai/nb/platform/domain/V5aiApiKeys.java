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
 * <p>明文 Key 形如 {@code v5ai_<32 位随机串>}，仅创建时返回一次；库内只存 BCrypt 密文。
 * 因 BCrypt 无法反查行，明文内嵌的 UUID（第 6–41 位）固化写入 {@code tracking_id} 作为运行时定位手柄。</p>
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
     * 跟踪 ID：明文 Key 内嵌的 UUID（36 位），运行时在唯一索引上定位密钥行
     */
    private String trackingId;

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