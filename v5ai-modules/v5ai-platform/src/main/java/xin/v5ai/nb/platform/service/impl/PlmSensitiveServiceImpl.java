package xin.v5ai.nb.platform.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ArrayUtil;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.sensitive.core.SensitiveService;

/**
 * 脱敏服务实现，为 {@code @Sensitive} 注解提供「是否脱敏」判断。
 *
 * <p>注解上的 {@code roleKey} / {@code perms} 表示<b>允许查看明文的角色/权限</b>：
 * 命中任一角色或权限的登录用户不脱敏；未登录、未命中且非超管的用户脱敏；
 * 超管（{@code user_id = 1}）始终不脱敏。
 */
@Service
public class PlmSensitiveServiceImpl implements SensitiveService {

    /**
     * 是否脱敏
     */
    @Override
    public boolean isSensitive(String[] roleKey, String[] perms) {
        if (!LoginHelper.isLogin()) {
            return true;
        }
        boolean roleExist = ArrayUtil.isNotEmpty(roleKey);
        boolean permsExist = ArrayUtil.isNotEmpty(perms);
        if (roleExist && permsExist) {
            if (StpUtil.hasRoleOr(roleKey) && StpUtil.hasPermissionOr(perms)) {
                return false;
            }
        } else if (roleExist && StpUtil.hasRoleOr(roleKey)) {
            return false;
        } else if (permsExist && StpUtil.hasPermissionOr(perms)) {
            return false;
        }

        return !LoginHelper.isSuperAdmin();
    }

}