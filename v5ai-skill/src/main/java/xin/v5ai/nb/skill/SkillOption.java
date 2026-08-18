package xin.v5ai.nb.skill;

/**
 * Skill 下拉选项的轻量视图（避免为下拉框加载描述等重字段）。
 *
 * @param id               主键
 * @param name             技能名
 * @param status           启用状态
 * @param currentVersionId 当前生效版本 ID（可为 null）
 * @param currentVersion   当前生效版本号（可为 null）
 */
public record SkillOption(
        Long id,
        String name,
        SkillStatus status,
        Long currentVersionId,
        Long currentVersion
) {
    /**
     * 是否为可绑定选项：启用且有已发布版本（与 {@code SkillManagementService.requireBindableSkill} 规则一致）。
     */
    public boolean isBindable() {
        return status == SkillStatus.ACTIVE && currentVersionId != null;
    }
}
