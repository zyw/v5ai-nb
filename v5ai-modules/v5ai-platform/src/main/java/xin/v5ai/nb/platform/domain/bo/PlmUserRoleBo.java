package xin.v5ai.nb.platform.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;
import xin.v5ai.nb.platform.domain.PlmUserRole;

/**
 * 用户和角色关联业务对象 sys_user_role
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PlmUserRole.class, reverseConvertGenerate = false)
public class PlmUserRoleBo extends BaseEntity {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空", groups = { EditGroup.class })
    private Long userId;

    /**
     * 角色ID
     */
    @NotNull(message = "角色ID不能为空", groups = { EditGroup.class })
    private Long roleId;


}
