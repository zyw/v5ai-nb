package xin.v5ai.nb.platform.domain.bo;

import xin.v5ai.nb.domain.SysRole;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 角色信息业务对象 sys_role
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysRole.class, reverseConvertGenerate = false)
public class SysRoleBo extends BaseEntity {

    /**
     * 角色ID
     */
    @NotNull(message = "角色ID不能为空", groups = { EditGroup.class })
    private Long roleId;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String roleName;

    /**
     * 角色权限字符串
     */
    @NotBlank(message = "角色权限字符串不能为空", groups = { AddGroup.class, EditGroup.class })
    private String roleKey;

    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long roleSort;

    /**
     * 数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）
     */
    private String dataScope;

    /**
     * 菜单树选择项是否关联显示
     */
    private Long menuCheckStrictly;

    /**
     * 部门树选择项是否关联显示
     */
    private Long deptCheckStrictly;

    /**
     * 角色状态（0正常 1停用）
     */
    @NotBlank(message = "角色状态（0正常 1停用）不能为空", groups = { AddGroup.class, EditGroup.class })
    private String status;

    /**
     * 备注
     */
    private String remark;


}
