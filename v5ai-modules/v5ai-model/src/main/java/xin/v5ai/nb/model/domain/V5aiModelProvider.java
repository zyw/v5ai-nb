package xin.v5ai.nb.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

/**
 * <p>
 * 
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_model_provider")
public class V5aiModelProvider extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String providerKey;

    private String name;

    private Boolean enabled;

    /**
     * 提供商描述
     */
    private String description;

    /**
     * LOGO 图标 URL
     */
    private String iconUrl;
}
