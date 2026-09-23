package xin.v5ai.nb.skill.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.skill.domain.Skill;

/**
 * Skill 列表查询请求体。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = Skill.class, reverseConvertGenerate = false)
public class SkillBo {

    /**
     * 技能名（模糊匹配）
     */
    private String name;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;
}
