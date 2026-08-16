package xin.v5ai.nb.infrastructure.agent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_agent_version")
public class AgentVersionEntity {
    @TableField("agent_key")
    private String agentKey;
    private Long version;
    @TableField("snapshot_json")
    private String snapshotJson;
    private String description;

    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
