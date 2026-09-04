package xin.v5ai.nb.model.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.model.domain.V5aiModelProvider;

/**
 * 模型供应商请求体。
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Data
@AutoMapper(target = V5aiModelProvider.class, reverseConvertGenerate = false)
public class ModelProviderBo {

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 供应商标识，创建后不可修改
     */
    @NotBlank(message = "供应商标识不能为空")
    private String providerKey;

    /**
     * 供应商名称
     */
    @NotBlank(message = "模型供应商名称不能为空")
    private String name;

    /**
     * 是否启用，默认 true
     */
    private Boolean enabled;

    /**
     * 提供商描述
     */
    private String description;

    /**
     * LOGO 图标 URL
     */
    private String iconUrl;

    /**
     * 搜索关键词（供应商名称或 key 模糊匹配，OR 关系）
     */
    private String keyword;
}
