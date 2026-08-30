//package xin.v5ai.nb.common.core.platform;
//
//import java.util.List;
//
///**
// * 用户账号存储：密码哈希校验 + 账号/角色管理。
// */
//public interface UserAccountStore {
//    /** 按用户名查找（含角色；密码哈希通过 findPasswordHash 单独获取）。 */
//    UserAccount findByUsername(String username);
//
//    UserAccount findById(Long id);
//
//    List<UserAccount> list();
//
//    UserAccount create(String username, String passwordHash, String displayName, List<String> roles);
//
//    void updateRoles(Long userId, List<String> roles);
//
//    /** 更新账号状态（'0'正常 '1'停用）。 */
//    void setStatus(Long userId, String status);
//
//    /** 登录成功后回写最后登录 IP 与时间。 */
//    void updateLoginInfo(Long userId, String loginIp, java.time.Instant loginDate);
//}
