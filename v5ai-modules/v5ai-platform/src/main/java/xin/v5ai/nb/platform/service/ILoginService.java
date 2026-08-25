package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.LoginUser;
import xin.v5ai.nb.platform.auth.LoginRequest;
import xin.v5ai.nb.platform.auth.LoginResponse;
import xin.v5ai.nb.platform.domain.vo.LoginVo;
import xin.v5ai.nb.platform.domain.vo.UserVo;

/**
 * 登录相关服务
 */

public interface ILoginService {

    /**
     * 登录
     */
    LoginVo login(LoginRequest request);

    /**
     * 登出
     */
    void logout();

    /**
     * 记录登录信息
     */
    void recordLoginInfo(String username, String status, String message);

    /**
     * 更新最后登录信息
     */
    void updateLastLoginInfo(Long userId, String ip);

    /**
     * 构建登录用户
     */
    LoginUser buildLoginUser(UserVo user);
}
