package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_agent_state")
public class AgentStateEntity {
    @TableId(value = "conversation_id")
    private String conversationId;
    @TableField("state_json")
    private String stateJson;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
}
