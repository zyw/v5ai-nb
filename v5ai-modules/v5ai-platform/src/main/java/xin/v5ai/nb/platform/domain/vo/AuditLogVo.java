//package xin.v5ai.nb.platform.domain.vo;
//
//import com.baomidou.mybatisplus.annotation.TableId;
//import io.github.linpeilie.annotations.AutoMapper;
//import lombok.Data;
//import xin.v5ai.nb.platform.domain.AuditLog;
//
//import java.io.Serial;
//import java.io.Serializable;
//import java.time.OffsetDateTime;
//
//@Data
//@AutoMapper(target = AuditLog.class)
//public class AuditLogVo implements Serializable {
//    @Serial
//    private static final long serialVersionUID = 1L;
//
//    @TableId
//    private Long id;
//    private Long tenantId;
//    private String actor;
//    private String action;
//    private String targetType;
//    private String targetId;
//    private String detail;
//    private String ip;
//    private OffsetDateTime createdAt;
//}
