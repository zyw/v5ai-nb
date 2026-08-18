package xin.v5ai.nb.api.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.platform.Menu;
import xin.v5ai.nb.common.core.platform.MenuStore;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.common.core.service.PermissionService;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final UserAccountStore userAccountStore;
    private final MenuStore menuStore;

    @Override
    public Set<String> getRolePermission(Long userId) {
        var account = userAccountStore.findById(userId);
        if (account == null || account.roles() == null) {
            log.warn("user {} has no roles", userId);
            return Set.of();
        }
        return new HashSet<>(account.roles());
    }

    @Override
    public Set<String> getMenuPermission(Long userId) {
        var account = userAccountStore.findById(userId);
        if (account == null || account.roles() == null || account.roles().isEmpty()) {
            log.warn("user {} has no roles for menu", userId);
            return Set.of();
        }
        return menuStore.listByRoleKeys(account.roles()).stream()
                .map(Menu::code)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }
}
