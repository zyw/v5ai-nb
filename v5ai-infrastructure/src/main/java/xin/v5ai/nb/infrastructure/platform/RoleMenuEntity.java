package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_role_menu")
public class RoleMenuEntity {
    @TableId(value = "role_key")
    private String roleKey;
    @TableField("menu_id")
    private Long menuId;

    public String getRoleKey() { return roleKey; }
    public void setRoleKey(String roleKey) { this.roleKey = roleKey; }
    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
}
