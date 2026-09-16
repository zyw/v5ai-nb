package xin.v5ai.nb.skill.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * AgentDTO-Skill 绑定实体（v5ai_agent_skill）：复合主键 (agent_key, skill_id)，无自增主键列。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@TableName("v5ai_agent_skill")
public class AgentSkillBinding implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * AgentDTO 对外标识
     */
    private String agentKey;

    /**
     * 绑定的 Skill
     */
    private Long skillId;

    private OffsetDateTime createdAt;
}
