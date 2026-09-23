package xin.v5ai.nb.skill.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;
import xin.v5ai.nb.skill.domain.enums.SkillStatus;

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
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_skill")
public class Skill extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
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
    private SkillStatus status;

    /**
     * 当前生效（注入运行时）的已发布版本 ID
     */
    private Long currentVersionId;
}
