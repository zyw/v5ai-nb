package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.platform.domain.vo.UserVo;

public interface IUserService {

    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    UserVo selectUserById(Long userId);

}
