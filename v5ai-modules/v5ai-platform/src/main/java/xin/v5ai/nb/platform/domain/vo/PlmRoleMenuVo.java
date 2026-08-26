package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.PlmRoleMenu;

import java.io.Serial;
import java.io.Serializable;


/**
 * 角色和菜单关联视图对象 sys_role_menu
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@AutoMapper(target = PlmRoleMenu.class)
public class PlmRoleMenuVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 菜单ID
     */
    private Long menuId;


}
