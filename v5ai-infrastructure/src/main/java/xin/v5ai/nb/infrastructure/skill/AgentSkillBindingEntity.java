package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_agent_skill")
public class AgentSkillBindingEntity {
    @TableId(value = "agent_key")
    private String agentKey;
    @TableField("skill_id")
    private Long skillId;

    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
}
