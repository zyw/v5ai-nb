package xin.v5ai.nb.infrastructure.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v5ai_model")
public class ModelConfigEntity {
    @TableId
    private Long id;
    private Long providerId;
    private String modelKey;
    private String modelType;
    private String credentialsCiphertext;
    private Boolean enabled;
}
