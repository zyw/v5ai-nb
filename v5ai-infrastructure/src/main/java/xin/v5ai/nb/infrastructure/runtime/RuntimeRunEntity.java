package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_run")
public class RuntimeRunEntity {
    @TableId
    private String id;
    private String conversationId;
    private String appKey;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
