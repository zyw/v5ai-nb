package xin.v5ai.nb.infrastructure.agent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_agent")
public class AgentEntity {
    @TableId
    private Long id;
    @TableField("agent_key")
    private String agentKey;
    private String name;
    private String description;
    private String status;
    @TableField("model_id")
    private Long modelId;
    @TableField("published_version")
    private Long publishedVersion;
    @TableField("system_prompt")
    private String systemPrompt;
    private String avatar;
    private String greeting;
    @TableField("preset_questions")
    private String presetQuestions;
    @TableField("memory_enabled")
    private Boolean memoryEnabled;
    @TableField("mcp_enabled")
    private Boolean mcpEnabled;
    @TableField("skill_enabled")
    private Boolean skillEnabled;
    @TableField("web_search_enabled")
    private Boolean webSearchEnabled;
    @TableField("rag_enabled")
    private Boolean ragEnabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(Long publishedVersion) { this.publishedVersion = publishedVersion; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public String getPresetQuestions() { return presetQuestions; }
    public void setPresetQuestions(String presetQuestions) { this.presetQuestions = presetQuestions; }
    public Boolean getMemoryEnabled() { return memoryEnabled; }
    public void setMemoryEnabled(Boolean memoryEnabled) { this.memoryEnabled = memoryEnabled; }
    public Boolean getMcpEnabled() { return mcpEnabled; }
    public void setMcpEnabled(Boolean mcpEnabled) { this.mcpEnabled = mcpEnabled; }
    public Boolean getSkillEnabled() { return skillEnabled; }
    public void setSkillEnabled(Boolean skillEnabled) { this.skillEnabled = skillEnabled; }
    public Boolean getWebSearchEnabled() { return webSearchEnabled; }
    public void setWebSearchEnabled(Boolean webSearchEnabled) { this.webSearchEnabled = webSearchEnabled; }
    public Boolean getRagEnabled() { return ragEnabled; }
    public void setRagEnabled(Boolean ragEnabled) { this.ragEnabled = ragEnabled; }
}
