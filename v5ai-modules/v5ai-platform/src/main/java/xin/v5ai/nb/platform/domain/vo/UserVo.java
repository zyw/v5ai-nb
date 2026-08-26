package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.User;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AutoMapper(target = User.class)
public class UserVo implements Serializable {
    private Long id;
    private String username;

    private String passwordHash;

    private Long tenantId;

    private String displayName;
    private String email;

    private String phoneNumber;
    private String gender;
    private Long avatar;
    private String status;

    private String delFlag;

    private String loginIp;

    private OffsetDateTime loginDate;
    private String remark;

    /**
     * 角色对象
     */
    private List<RoleVo> roles;
}
