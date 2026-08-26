package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_role")
public class Role implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("role_key")
    private String roleKey;
    @TableField("role_name")
    private String roleName;
    private String description;
    @TableField("data_scope")
    private String dataScope;
    private Boolean builtin;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
