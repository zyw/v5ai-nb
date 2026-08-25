package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 平台用户（MyBatis 实体）。账号状态由 status（'0'正常 '1'停用）控制。
 *
 * <p>注意：表列 created_at/updated_at 为 TIMESTAMPTZ，与平台其余实体一致用
 * {@link Instant} 承载（BaseEntity 的 LocalDateTime 无法被 PG 驱动映射
 * TIMESTAMPTZ，会导致 selectList 报错），因此不继承 BaseEntity。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_user")
public class User extends BaseEntity {

    @Serial
    private final static long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String username;
    @TableField("password_hash")
    private String passwordHash;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("display_name")
    private String displayName;
    private String email;
    @TableField("phone_number")
    private String phoneNumber;
    private String gender;
    private Long avatar;
    private String status;
    @TableField("del_flag")
    private String delFlag;
    @TableField("login_ip")
    private String loginIp;
    @TableField("login_date")
    private OffsetDateTime loginDate;
    private String remark;
}
