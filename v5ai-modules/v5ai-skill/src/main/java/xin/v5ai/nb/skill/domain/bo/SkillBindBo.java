package xin.v5ai.nb.skill.domain.bo;

import java.util.List;

/**
 * AgentDTO 绑定 Skill 请求体。
 *
 * @param skillIds 要绑定的 Skill ID 集合（全量替换）
 */
public record SkillBindBo(List<Long> skillIds) {
}
