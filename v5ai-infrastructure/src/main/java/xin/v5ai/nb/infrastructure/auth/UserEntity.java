package xin.v5ai.nb.infrastructure.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("v5ai_user")
public class UserEntity {
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
    private Instant loginDate;
    private String remark;
}
