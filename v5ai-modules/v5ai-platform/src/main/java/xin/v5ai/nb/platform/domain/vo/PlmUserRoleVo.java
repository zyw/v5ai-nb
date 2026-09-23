package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.PlmUserRole;

import java.io.Serial;
import java.io.Serializable;


/**
 * 用户和角色关联视图对象 sys_user_role
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@AutoMapper(target = PlmUserRole.class)
public class PlmUserRoleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;


}
