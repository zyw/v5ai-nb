package xin.v5ai.nb.skill.domain.enums;

/**
 * Skill 版本状态三态：
 * DRAFT（编辑中，唯一可编辑）/ PUBLISHED（已发布，线上注入「当前版本」）/
 * OFFLINE（已下线：曾发布后撤下，内容冻结不可编辑，可重新发布或删除）。
 */
public enum SkillVersionStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE
}
