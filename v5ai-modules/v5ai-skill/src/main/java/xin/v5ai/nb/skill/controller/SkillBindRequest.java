package xin.v5ai.nb.skill.controller;

import java.util.List;

/**
 * Agent 绑定 Skill 请求体。
 *
 * @param skillIds 要绑定的 Skill ID 集合（全量替换）
 */
public record SkillBindRequest(List<Long> skillIds) {
}
