package xin.v5ai.nb.skill.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target= AgentSkillBinding.class)
public class AgentSkillBindingVo implements Serializable {

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
