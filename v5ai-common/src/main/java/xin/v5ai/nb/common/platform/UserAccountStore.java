package xin.v5ai.nb.common.platform;

import java.util.List;

/**
 * 用户账号存储：密码哈希校验 + 账号/角色管理。
 */
public interface UserAccountStore {
    /** 按用户名查找（含角色；密码哈希通过 findPasswordHash 单独获取）。 */
    UserAccount findByUsername(String username);

    UserAccount findById(Long id);

    List<UserAccount> list();

    UserAccount create(String username, String passwordHash, String displayName, Long tenantId, List<String> roles);

    void updateRoles(Long userId, List<String> roles);

    void setEnabled(Long userId, boolean enabled);
}
