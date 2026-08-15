package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_application_mcp")
public class ApplicationMcpBindingEntity {
    @TableId(value = "app_key")
    private String appKey;
    @TableField("mcp_server_id")
    private Long mcpServerId;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public Long getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(Long mcpServerId) { this.mcpServerId = mcpServerId; }
}
