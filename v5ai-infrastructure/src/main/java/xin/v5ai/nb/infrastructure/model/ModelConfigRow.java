package xin.v5ai.nb.infrastructure.model;

/**
 * Joined view row of {@code v5ai_model} + {@code v5ai_model_provider} used for admin listings.
 */
public class ModelConfigRow {
    private Long id;
    private Long providerId;
    private String providerKey;
    private String modelKey;
    private String modelType;
    private String credentialsCiphertext;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getCredentialsCiphertext() { return credentialsCiphertext; }
    public void setCredentialsCiphertext(String credentialsCiphertext) { this.credentialsCiphertext = credentialsCiphertext; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
