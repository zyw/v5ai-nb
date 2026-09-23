package xin.v5ai.nb.platform.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;
import xin.v5ai.nb.platform.domain.PlmRoleMenu;

/**
 * 角色和菜单关联业务对象 sys_role_menu
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PlmRoleMenu.class, reverseConvertGenerate = false)
public class PlmRoleMenuBo extends BaseEntity {

    /**
     * 角色ID
     */
    @NotNull(message = "角色ID不能为空", groups = { EditGroup.class })
    private Long roleId;

    /**
     * 菜单ID
     */
    @NotNull(message = "菜单ID不能为空", groups = { EditGroup.class })
    private Long menuId;


}
