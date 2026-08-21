package xin.v5ai.nb.model.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.model.domain.V5aiModelProvider;

@Data
@AutoMapper(target = V5aiModelProvider.class, reverseConvertGenerate = false)
public class ModelProviderBo {
    @NotBlank(message = "id不能为空",groups = {EditGroup.class})
    private Long id;
    @NotBlank(message = "模型供应商名称不能为空")
    private String name;
    @NotBlank(message = "供应商标识不能为空")
    private String providerKey;
    @NotBlank(message = "providerType不能为空")
    private String providerType;
    private boolean enabled;
}
