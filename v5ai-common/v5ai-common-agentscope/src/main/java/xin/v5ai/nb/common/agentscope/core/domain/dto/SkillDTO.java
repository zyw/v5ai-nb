package xin.v5ai.nb.common.agentscope.core.domain.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * Skill 实体（v5ai_skill）：技能名全局唯一，status 为 ACTIVE/DISABLED。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
public class SkillDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 技能名（来自 SKILL.md frontmatter，全局唯一）
     */
    private String name;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    /**
     * 当前生效（注入运行时）的已发布版本 ID
     */
    private Long currentVersionId;
}
