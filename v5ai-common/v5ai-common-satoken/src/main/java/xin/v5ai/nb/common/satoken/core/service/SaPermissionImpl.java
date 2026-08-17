package xin.v5ai.nb.common.satoken.core.service;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import xin.v5ai.nb.common.core.domain.LoginUser;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.service.PermissionService;
import xin.v5ai.nb.common.core.utils.SpringUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * sa-token 权限管理实现类
 *
 * @author Lion Li
 */
public class SaPermissionImpl implements StpInterface {

    /**
     * 获取菜单权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return resolvePermissionList(loginId, LoginUser::getMenuPermission, PermissionService::getMenuPermission);
    }

    /**
     * 获取角色权限列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return resolvePermissionList(loginId, LoginUser::getRolePermission, PermissionService::getRolePermission);
    }

    /**
     * 解析当前登录对象的权限列表。
     *
     * @param loginId                   登录ID
     * @param localPermissionExtractor  当前登录用户权限提取器
     * @param remotePermissionExtractor 远程权限提取器
     * @return 权限列表
     */
    private List<String> resolvePermissionList(Object loginId,
                                               Function<LoginUser, Collection<String>> localPermissionExtractor,
                                               BiFunction<PermissionService, Long, Collection<String>> remotePermissionExtractor) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (ObjectUtil.isNull(loginUser) || !loginUser.getLoginId().equals(loginId)) {
            PermissionService permissionService = getPermissionService();
            if (ObjectUtil.isNotNull(permissionService)) {
                return new ArrayList<>(remotePermissionExtractor.apply(permissionService, resolveUserId(loginId)));
            }
            throw new V5aiException(ErrorCode.UNAUTHORIZED, "PermissionService 实现类不存在");
        }
        Collection<String> permissionList = localPermissionExtractor.apply(loginUser);
        if (CollUtil.isNotEmpty(permissionList)) {
            return new ArrayList<>(permissionList);
        }
        return new ArrayList<>();
    }

    private PermissionService getPermissionService() {
        try {
            return SpringUtils.getBean(PermissionService.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从登录ID中提取用户ID。
     *
     * @param loginId 登录ID
     * @return 用户ID
     */
    private Long resolveUserId(Object loginId) {
        String loginIdStr = loginId.toString();
        int separatorIndex = loginIdStr.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == loginIdStr.length() - 1) {
            throw new V5aiException(ErrorCode.UNAUTHORIZED, "登录ID格式错误");
        }
        return Long.parseLong(loginIdStr.substring(separatorIndex + 1));
    }

}
