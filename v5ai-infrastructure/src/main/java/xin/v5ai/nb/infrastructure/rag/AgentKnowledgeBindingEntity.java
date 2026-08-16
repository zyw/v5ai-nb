package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_agent_knowledge")
public class AgentKnowledgeBindingEntity {
    @TableId(value = "agent_key")
    private String agentKey;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
}
