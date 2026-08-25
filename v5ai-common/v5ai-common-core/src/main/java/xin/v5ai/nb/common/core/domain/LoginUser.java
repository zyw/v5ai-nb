package xin.v5ai.nb.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import xin.v5ai.nb.common.core.domain.dto.RoleDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录用户上下文对象，保存当前会话的身份、权限和终端信息。
 *
 * @author Lion Li
 */
@Data
@NoArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 菜单权限
     */
    private Set<String> menuPermission;

    /**
     * 角色权限
     */
    private Set<String> rolePermission;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 角色对象
     */
    private List<RoleDTO> roles;

    /**
     * 数据权限角色映射 key 为 dataScope 码 value 为可参与数据权限计算的角色 roleKey 列表
     */
    private Map<String, List<String>> dataScopeRoleMap;

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 获取 Sa-Token 使用的登录标识。
     *
     * @return 登录标识
     */
    public String getLoginId() {
        return "v5ai-user:" + userId;
    }

}
