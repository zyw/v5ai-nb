package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.Role;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = Role.class)
public class RoleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String roleKey;

    private String roleName;
    private String description;

    private String dataScope;
    private Boolean builtin;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
