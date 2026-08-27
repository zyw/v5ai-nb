package xin.v5ai.nb.platform.service.impl;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.auth.UserCredentialStore;
import xin.v5ai.nb.common.core.constant.CacheNames;
import xin.v5ai.nb.common.core.constant.Constants;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.domain.LoginUser;
import xin.v5ai.nb.common.core.domain.dto.RoleDTO;
import xin.v5ai.nb.common.core.domain.model.PasswordLoginBody;
import xin.v5ai.nb.common.core.enums.LoginType;
import xin.v5ai.nb.common.core.exception.user.UserException;
import xin.v5ai.nb.common.core.platform.Role;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.common.core.service.PermissionService;
import xin.v5ai.nb.common.core.utils.*;
import xin.v5ai.nb.common.json.utils.JsonUtils;
import xin.v5ai.nb.common.log.enums.OperatorType;
import xin.v5ai.nb.common.log.event.LoginInfoEvent;
import xin.v5ai.nb.common.mybatis.helper.DataPermissionHelper;
import xin.v5ai.nb.common.redis.utils.RedisUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.controller.auth.LoginRequest;
import xin.v5ai.nb.platform.domain.PlmUser;
import xin.v5ai.nb.platform.domain.User;
import xin.v5ai.nb.platform.domain.vo.LoginVo;
import xin.v5ai.nb.platform.domain.vo.PlmClientVo;
import xin.v5ai.nb.platform.domain.vo.PlmUserVo;
import xin.v5ai.nb.platform.domain.vo.UserVo;
import xin.v5ai.nb.platform.mapper.PlmUserMapper;
import xin.v5ai.nb.platform.mapper.UserMapper;
import xin.v5ai.nb.platform.security.PasswordVerifier;
import xin.v5ai.nb.platform.service.ILoginService;
import xin.v5ai.nb.platform.service.IRoleService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements ILoginService {

    /**
     * 最大重试次数。
     */
    @Value("${user.password.maxRetryCount:5}")
    private Integer maxRetryCount;

    /**
     * 锁定时间。
     */
    @Value("${user.password.lockTime:10}")
    private Integer lockTime;

    private final UserCredentialStore credentialStore;
    private final UserAccountStore userAccountStore;
    private final PasswordVerifier passwordVerifier;
//    private final AuditLogRecorderService auditRecorder;
    private final PermissionService permissionService;
    private final IRoleService roleService;
    private final SaTokenConfig saTokenConfig;

    private final PlmUserMapper userMapper;

    @Override
    public LoginVo login(String body, PlmClientVo client) {
        PasswordLoginBody loginBody = JsonUtils.parseObject(body, PasswordLoginBody.class);
        ValidatorUtils.validate(loginBody);
        String username = loginBody.getUsername();
        String password = loginBody.getPassword();
        String code = loginBody.getCode();
        String uuid = loginBody.getUuid();

        boolean captchaEnabled = captchaProperties.getEnable();
        // 验证码开关
        if (captchaEnabled) {
            validateCaptcha(username, code, uuid);
        }
        PlmUserVo user = loadUserByUsername(username);
        checkLogin(LoginType.PASSWORD, username, () -> !BCrypt.checkpw(password, user.getPassword()));
        // 此处可根据登录用户的数据不同 自行创建 loginUser
        LoginUser loginUser = buildLoginUser(user);
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        SaLoginParameter model = buildLoginParameter(client);
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    @Override
    public LoginVo login(LoginRequest request) {
//        var account = userAccountStore.findByUsername(request.username());
        // 1. 加载用户
        var userInfo = loadUserByUsername(request.username());
//        var passwordHash = credentialStore.findPasswordHash(request.username());
        // 2. 验证密码
        if (!passwordVerifier.matches(request.password(), userInfo.getPasswordHash())) {
            recordLoginInfo(request.username(), Constants.LOGIN_FAIL, MessageUtils.message("user.login.fail"));
            log.warn("invalid credentials for {}", request.username());
            throw new IllegalArgumentException("invalid credentials");
        }
        // 3. 验证账号状态
        if (!"0".equals(userInfo.getStatus())) {
            log.warn("account {} is disabled", userInfo.getUsername());
            throw new IllegalArgumentException("account is disabled");
        }

        // 此处可根据登录用户的数据不同 自行创建 loginUser
        // 4. 构建权限
        LoginUser loginUser = buildLoginUser(userInfo);
        loginUser.setDeviceType(OperatorType.MANAGE.getCode());
        SaLoginParameter model = new SaLoginParameter();
        model.setTimeout(saTokenConfig.getTimeout());
        model.setActiveTimeout(saTokenConfig.getActiveTimeout());

//        var roles = account.roles() == null ? List.<String>of() : account.roles();
        // 登录成功：回写最后登录 IP 与时间（失败不阻断登录）
//        recordLoginInfo(account.id());
//        return new LoginResponse("Bearer", StpUtil.getTokenValue(), userInfo.getId(), userInfo.getUsername(),
//                userInfo.getDisplayName(), userInfo.getTenantId(), loginUser.getRolePermission());
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
//        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    /**
     * 登录成功后回写最后登录 IP 与时间。无请求上下文（如测试直调）时 IP 记为空串；
     * 回写失败仅告警，不影响登录流程。
     */
//    private void recordLoginInfo(Long userId) {
//        try {
//            String loginIp;
//            try {
//                loginIp = ServletUtils.getClientIP();
//            } catch (Exception ignored) {
//                loginIp = "";
//            }
//            userAccountStore.updateLoginInfo(userId, loginIp, Instant.now());
//        } catch (Exception exception) {
//            log.warn("failed to record login info for user {}: {}", userId, exception.getMessage());
//        }
//    }

    @Override
    public void logout() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            if (ObjectUtil.isNull(loginUser)) {
                return;
            }
            recordLoginInfo(loginUser.getUsername(), Constants.LOGOUT, MessageUtils.message("user.logout.success"));
        } catch (NotLoginException ignored) {
        } finally {
            try {
                StpUtil.logout();
            } catch (NotLoginException ignored) {
            }
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     */
    @Override
    public void recordLoginInfo(String username, String status, String message) {
        LoginInfoEvent loginInfoEvent = new LoginInfoEvent();
        loginInfoEvent.setUsername(username);
        loginInfoEvent.setStatus(status);
        loginInfoEvent.setMessage(message);
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            loginInfoEvent.setIp(ServletUtils.getClientIP(request));
            loginInfoEvent.setUserAgent(request.getHeader("User-Agent"));
//            loginInfoEvent.setClientId(request.getHeader(LoginHelper.CLIENT_KEY));
        }
        SpringUtils.context().publishEvent(loginInfoEvent);
    }

    /**
     * 更新用户最近一次登录IP与登录时间。
     *
     * @param userId 用户ID
     * @param ip     登录IP
     */
    public void updateLastLoginInfo(Long userId, String ip) {
        User sysUser = new User();
        sysUser.setId(userId);
        sysUser.setLoginIp(ip);
        sysUser.setLoginDate(OffsetDateTime.now());
        sysUser.setUpdatedAt(OffsetDateTime.now());
        DataPermissionHelper.ignore(() -> userMapper.updateById(sysUser));
    }

    /**
     * 根据用户视图对象组装登录态上下文。
     *
     * @param user 用户基础信息
     * @return 包含部门、角色、岗位与权限数据的登录用户
     */
    @Override
    public LoginUser buildLoginUser(PlmUserVo user) {
        LoginUser loginUser = new LoginUser();
        Long userId = user.getId();
        loginUser.setUserId(userId);
        loginUser.setUsername(user.getUserName());
        loginUser.setNickname(user.getNickName());
        ThreadUtils.virtualInvokeAll(() -> {
            loginUser.setMenuPermission(permissionService.getMenuPermission(userId));
        }, () -> {
            loginUser.setRolePermission(permissionService.getRolePermission(userId));
        }, () -> {
            List<Role> roles = roleService.selectRolesByUserId(userId);
            List<RoleDTO> roleDtos = BeanUtil.copyToList(roles, RoleDTO.class);
            loginUser.setRoles(roleDtos);
            loginUser.setDataScopeRoleMap(permissionService.getDataScopeRoleMap(roleDtos));
        });
        return loginUser;
    }

    /**
     * 执行登录失败次数校验，并在成功后清空失败计数。
     *
     * @param loginType 登录类型
     * @param username  登录标识
     * @param supplier  返回 {@code true} 表示本次认证失败
     */
    public void checkLogin(LoginType loginType, String username, Supplier<Boolean> supplier) {
        String errorKey = CacheNames.PWD_ERR_CNT_KEY + username;
        String loginFail = Constants.LOGIN_FAIL;

        // 获取用户登录错误次数，默认为0 (可自定义限制策略 例如: key + username + ip)
        int errorNumber = ObjectUtil.defaultIfNull(RedisUtils.getCacheObject(errorKey), 0);
        // 锁定时间内登录 则踢出
        if (errorNumber >= maxRetryCount) {
            recordLoginInfo(username, loginFail, MessageUtils.message(loginType.getRetryLimitExceed(), maxRetryCount, lockTime));
            throw new UserException(loginType.getRetryLimitExceed(), maxRetryCount, lockTime);
        }

        if (supplier.get()) {
            // 错误次数递增
            errorNumber++;
            RedisUtils.setCacheObject(errorKey, errorNumber, Duration.ofMinutes(lockTime));
            // 达到规定错误次数 则锁定登录
            if (errorNumber >= maxRetryCount) {
                recordLoginInfo(username, loginFail, MessageUtils.message(loginType.getRetryLimitExceed(), maxRetryCount, lockTime));
                throw new UserException(loginType.getRetryLimitExceed(), maxRetryCount, lockTime);
            } else {
                // 未达到规定错误次数
                recordLoginInfo(username, loginFail, MessageUtils.message(loginType.getRetryLimitCount(), errorNumber));
                throw new UserException(loginType.getRetryLimitCount(), errorNumber);
            }
        }

        // 登录成功 清空错误次数
        RedisUtils.deleteObject(errorKey);
    }

    /**
     * 按用户名加载可登录用户，并校验是否存在或被停用。
     *
     * @param username 用户名
     * @return 用户信息
     */
    private PlmUserVo loadUserByUsername(String username) {
        PlmUserVo user = userMapper.lambda()
                .eq(PlmUser::getUserName, username)
                .voOne();
        if (ObjectUtil.isNull(user)) {
            log.info("登录用户：{} 不存在.", username);
            throw new UserException("user.not.exists", username);
        } else if (SystemConstants.DISABLE.equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", username);
            throw new UserException("user.blocked", username);
        }
        return user;
    }
}
