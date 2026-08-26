package xin.v5ai.nb.platform.domain.bo;

import xin.v5ai.nb.domain.SysUserRole;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 用户和角色关联业务对象 sys_user_role
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysUserRole.class, reverseConvertGenerate = false)
public class SysUserRoleBo extends BaseEntity {

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
