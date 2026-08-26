package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 角色和菜单关联对象 sys_role_menu
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plm_role_menu")
public class PlmRoleMenu extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    @TableId(value = "role_id")
    private Long roleId;

    /**
     * 菜单ID
     */
    @TableId(value = "menu_id")
    private Long menuId;


}
