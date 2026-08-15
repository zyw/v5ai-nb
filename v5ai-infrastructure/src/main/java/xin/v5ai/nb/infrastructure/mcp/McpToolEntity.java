package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("v5ai_mcp_tool")
public class McpToolEntity {
    @TableId
    private Long id;
    private Long serverId;
    private String toolName;
    private String description;
    private String inputSchemaJson;
    private Boolean readOnly;
    private String permission;
    private Instant lastDiscoveredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }
    public Boolean getReadOnly() { return readOnly; }
    public void setReadOnly(Boolean readOnly) { this.readOnly = readOnly; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public Instant getLastDiscoveredAt() { return lastDiscoveredAt; }
    public void setLastDiscoveredAt(Instant lastDiscoveredAt) { this.lastDiscoveredAt = lastDiscoveredAt; }
}
