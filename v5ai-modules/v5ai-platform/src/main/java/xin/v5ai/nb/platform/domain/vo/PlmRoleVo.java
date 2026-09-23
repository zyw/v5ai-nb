package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.platform.domain.PlmRole;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;


/**
 * 角色信息视图对象 sys_role
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@AutoMapper(target = PlmRole.class)
public class PlmRoleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    private Long id;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色权限字符串
     */
    private String roleKey;

    /**
     * 显示顺序
     */
    private Integer roleSort;

    /**
     * 数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）
     */
    private String dataScope;

    /**
     * 菜单树选择项是否关联显示
     */
    private Boolean menuCheckStrictly;

    /**
     * 角色状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 用户是否存在此角色标识 默认不存在
     */
    private boolean flag = false;

    /**
     * 判断当前角色是否为超级管理员角色。
     *
     * @return true 是超级管理员角色 false 不是超级管理员角色
     */
    public boolean isSuperAdmin() {
        return SystemConstants.SUPER_ADMIN_ROLE_ID.equals(this.id);
    }


}
