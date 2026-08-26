package xin.v5ai.nb.platform.domain.bo;

import xin.v5ai.nb.domain.SysRoleMenu;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 角色和菜单关联业务对象 sys_role_menu
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysRoleMenu.class, reverseConvertGenerate = false)
public class SysRoleMenuBo extends BaseEntity {

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
