package xin.v5ai.nb.infrastructure.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_model")
public class ModelConfigEntity {
    @TableId
    private Long id;
    private Long providerId;
    private String modelKey;
    private String modelType;
    private String credentialsCiphertext;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getCredentialsCiphertext() { return credentialsCiphertext; }
    public void setCredentialsCiphertext(String credentialsCiphertext) { this.credentialsCiphertext = credentialsCiphertext; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
