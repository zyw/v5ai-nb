package xin.v5ai.nb.platform.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.platform.Role;
import xin.v5ai.nb.platform.mapper.RoleMapper;
import xin.v5ai.nb.platform.mapper.UserRoleMapper;
import xin.v5ai.nb.platform.service.IRoleService;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements IRoleService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public List<Role> list() {
        return roleMapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Role findByKey(String roleKey) {
        return toDomain(roleMapper.selectById(roleKey));
    }

    @Override
    public Role create(String roleKey, String roleName, String description, String dataScope) {
        var entity = new xin.v5ai.nb.platform.domain.Role();
        entity.setRoleKey(roleKey);
        entity.setRoleName(roleName);
        entity.setDescription(description);
        entity.setDataScope(dataScope == null || dataScope.isBlank() ? "1" : dataScope);
        entity.setBuiltin(false);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        roleMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public Role update(String roleKey, String roleName, String description, String dataScope) {
        roleMapper.updateMeta(roleKey, roleName, description, dataScope == null || dataScope.isBlank() ? "1" : dataScope);
        return findByKey(roleKey);
    }

    @Override
    public void delete(String roleKey) {
        roleMapper.deleteById(roleKey);
    }

    @Override
    public long countUsers(String roleKey) {
        return userRoleMapper.countByRole(roleKey);
    }

    @Override
    public List<Role> selectRolesByUserId(Long userId) {
        return userRoleMapper.selectRolesByUser(userId).stream()
                .map(roleMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .map(this::toDomain)
                .toList();
    }

    private Role toDomain(xin.v5ai.nb.platform.domain.Role entity) {
        if (entity == null) {
            return null;
        }
        return new Role(entity.getRoleKey(), entity.getRoleName(), entity.getDescription(),
                entity.getDataScope() == null ? "1" : entity.getDataScope(),
                Boolean.TRUE.equals(entity.getBuiltin()), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
