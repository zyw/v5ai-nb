package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_audit_log")
public class AuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long tenantId;
    private String actor;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private OffsetDateTime createdAt;
}
