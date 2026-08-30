//package xin.v5ai.nb.platform.service.impl;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
//import org.springframework.stereotype.Repository;
//import xin.v5ai.nb.common.core.platform.UserAccount;
//import xin.v5ai.nb.common.core.platform.UserAccountStore;
//import xin.v5ai.nb.platform.domain.User;
//import xin.v5ai.nb.platform.mapper.UserMapper;
//import xin.v5ai.nb.platform.mapper.UserRoleMapper;
//
//import java.util.List;
//
//@Repository
//public class MyBatisUserAccountStore implements UserAccountStore {
//    private final UserMapper userMapper;
//    private final UserRoleMapper roleMapper;
//
//    public MyBatisUserAccountStore(UserMapper userMapper, UserRoleMapper roleMapper) {
//        this.userMapper = userMapper;
//        this.roleMapper = roleMapper;
//    }
//
//    @Override
//    public UserAccount findByUsername(String username) {
//        var entity = userMapper.selectOne(new LambdaQueryWrapper<User>()
//                .eq(User::getUsername, username));
//        return toDomain(entity);
//    }
//
//    @Override
//    public UserAccount findById(Long id) {
//        return toDomain(userMapper.selectById(id));
//    }
//
//    @Override
//    public List<UserAccount> list() {
//        return userMapper.selectList(null).stream().map(this::toDomain).toList();
//    }
//
//    @Override
//    public UserAccount create(String username, String passwordHash, String displayName,
//                              List<String> roles) {
//        var entity = new User();
//        entity.setUsername(username);
//        entity.setPasswordHash(passwordHash);
//        entity.setStatus("0");
//        entity.setDisplayName(displayName);
//        userMapper.insert(entity);
//        if (roles != null && !roles.isEmpty()) {
//            roleMapper.insertRoles(entity.getId(), roles);
//        }
//        return toDomain(entity);
//    }
//
//    @Override
//    public void updateRoles(Long userId, List<String> roles) {
//        roleMapper.deleteByUser(userId);
//        if (roles != null && !roles.isEmpty()) {
//            roleMapper.insertRoles(userId, roles);
//        }
//    }
//
//    @Override
//    public void setStatus(Long userId, String status) {
//        var entity = new User();
//        entity.setStatus(status);
//        userMapper.update(entity, new LambdaUpdateWrapper<User>()
//                .eq(User::getId, userId));
//    }
//
//    @Override
//    public void updateLoginInfo(Long userId, String loginIp, java.time.Instant loginDate) {
//        userMapper.update(null, new LambdaUpdateWrapper<User>()
//                .eq(User::getId, userId)
//                .set(User::getLoginIp, loginIp == null ? "" : loginIp)
//                .set(User::getLoginDate, loginDate));
//    }
//
//    private UserAccount toDomain(User entity) {
//        if (entity == null) {
//            return null;
//        }
//        List<String> roles = roleMapper.selectRolesByUser(entity.getId());
//        return new UserAccount(entity.getId(), entity.getUsername(), entity.getDisplayName(),
//                roles, entity.getStatus());
//    }
//}
