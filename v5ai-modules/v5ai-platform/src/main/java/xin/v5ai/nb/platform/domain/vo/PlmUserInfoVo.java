package xin.v5ai.nb.platform.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户信息
 *
 * @author Michelle.Chung
 */
@Data
public class PlmUserInfoVo {

    /**
     * 用户信息
     */
    private PlmUserVo user;

    /**
     * 角色ID列表
     */
    private List<Long> roleIds;

    /**
     * 角色列表
     */
    private List<PlmRoleVo> roles;

    /**
     * 岗位ID列表
     */
    private List<Long> postIds;

}
