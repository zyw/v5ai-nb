package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户和角色关联对象 sys_user_role
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@TableName("plm_user_role")
public class PlmUserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;


}
