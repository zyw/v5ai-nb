package xin.v5ai.nb.platform.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.core.xss.Xss;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;
import xin.v5ai.nb.platform.domain.PlmUser;

/**
 * 用户信息业务对象 sys_user
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@AutoMapper(target = PlmUser.class, reverseConvertGenerate = false)
public class PlmUserBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户账号
     */
    @Xss(message = "用户账号不能包含脚本字符")
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 2, max = 30, message = "用户账号长度必须在{min}到{max}个字符之间")
    private String userName;

    /**
     * 用户昵称
     */
    @Xss(message = "用户昵称不能包含脚本字符")
    @NotBlank(message = "用户昵称不能为空")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 用户类型（sys_user系统用户）
     */
    private String userType;

    /**
     * 用户邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 手机号码
     */
    private String phoneNumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String gender;

    /**
     * 头像 OSS ID
     */
    private Long avatar;

    /**
     * 密码
     */
    private String password;

    /**
     * 账号状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 角色组
     */
    @Size(min = 1, message = "用户角色不能为空")
    private Long[] roleIds;

    /**
     * 岗位组
     */
    private Long[] postIds;

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 用户ID
     */
    private String userIds;

    /**
     * 排除不查询的用户(工作流用)
     */
    private String excludeUserIds;

    /**
     * 创建者
     */
    private Long createdBy;

    /**
     * 更新者
     */
    private Long updatedBy;

    /**
     * 请求参数
     */
    private Map<String, Object> params = new HashMap<>();

    /**
     * 判断当前用户是否为超级管理员。
     *
     * @return true 是超级管理员 false 不是超级管理员
     */
    public boolean isSuperAdmin() {
        return SystemConstants.SUPER_ADMIN_USER_ID.equals(this.id);
    }


}
