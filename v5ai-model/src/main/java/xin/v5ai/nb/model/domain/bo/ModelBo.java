package xin.v5ai.nb.model.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.model.ModelType;
import xin.v5ai.nb.model.domain.V5aiModel;

@Data
@AutoMapper(target = V5aiModel.class, reverseConvertGenerate = false)
public class ModelBo {

    @NotBlank(message = "id不能为空",groups = {EditGroup.class})
    private Long id;
    @NotBlank(message = "providerKey不能为空")
    private String providerKey;
    @NotBlank(message = "modelKey不能为空")
    private String modelKey;
    @NotBlank(message = "type不能为空")
    private ModelType type;
    @NotBlank(message = "credentials不能为空")
    private String credentials;
}
