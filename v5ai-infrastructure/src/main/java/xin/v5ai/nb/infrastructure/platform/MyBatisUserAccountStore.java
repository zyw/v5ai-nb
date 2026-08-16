package xin.v5ai.nb.infrastructure.platform;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.platform.RoleKey;
import xin.v5ai.nb.common.platform.UserAccount;
import xin.v5ai.nb.common.platform.UserAccountStore;
import xin.v5ai.nb.infrastructure.auth.UserEntity;

import java.util.List;

@Repository
public class MyBatisUserAccountStore implements UserAccountStore {
    private final UserAccountMapper userMapper;
    private final UserRoleMapper roleMapper;

    public MyBatisUserAccountStore(UserAccountMapper userMapper, UserRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserAccount findByUsername(String username) {
        var entity = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        return toDomain(entity);
    }

    @Override
    public UserAccount findById(Long id) {
        return toDomain(userMapper.selectById(id));
    }

    @Override
    public List<UserAccount> list() {
        return userMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public UserAccount create(String username, String passwordHash, String displayName,
                              Long tenantId, List<RoleKey> roles) {
        var entity = new UserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHash);
        entity.setEnabled(true);
        entity.setTenantId(tenantId);
        entity.setDisplayName(displayName);
        userMapper.insert(entity);
        if (roles != null && !roles.isEmpty()) {
            roleMapper.insertRoles(entity.getId(), roles.stream().map(Enum::name).toList());
        }
        return toDomain(entity);
    }

    @Override
    public void updateRoles(Long userId, List<RoleKey> roles) {
        roleMapper.deleteByUser(userId);
        if (roles != null && !roles.isEmpty()) {
            roleMapper.insertRoles(userId, roles.stream().map(Enum::name).toList());
        }
    }

    @Override
    public void setEnabled(Long userId, boolean enabled) {
        var entity = new UserEntity();
        entity.setEnabled(enabled);
        userMapper.update(entity, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, userId));
    }

    private UserAccount toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        List<RoleKey> roles = roleMapper.selectRolesByUser(entity.getId()).stream()
                .map(role -> {
                    try {
                        return RoleKey.valueOf(role);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new UserAccount(entity.getId(), entity.getUsername(), entity.getDisplayName(),
                entity.getTenantId(), roles, Boolean.TRUE.equals(entity.getEnabled()));
    }
}
