package xin.v5ai.nb.skill.core;

import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.enums.SkillVersionStatus;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Skill 版本生命周期纯规则模块（无 IO、无 Spring 依赖）：
 * 版本状态迁移合法性与 Skill 删除/禁用守卫的唯一裁决点。
 * <pre>
 * 状态机：DRAFT --发布--> PUBLISHED；PUBLISHED --下线--> OFFLINE；OFFLINE --重新发布--> PUBLISHED
 * 不变量：
 * - Skill 可禁用/删除 ⇔ 不存在任何 PUBLISHED 版本（与是否 current 无关）；
 * - 可编辑草稿唯一：最新版本号的 DRAFT；不存在时由调用方新建；
 * - 版本可删除 ⇔ 非 PUBLISHED（DRAFT/OFFLINE 均可清理）。
 * </pre>
 */
public final class SkillVersionLifecycle {

    private SkillVersionLifecycle() {
    }

    /** 该 Skill 是否存在已发布（PUBLISHED）版本。 */
    public static boolean hasPublishedVersion(Collection<SkillVersion> versions) {
        return versions.stream()
                .anyMatch(v -> v.getStatus() == SkillVersionStatus.PUBLISHED);
    }

    /** Skill 可禁用 ⇔ 无任何已发布版本。 */
    public static boolean canDisableSkill(Collection<SkillVersion> versions) {
        return !hasPublishedVersion(versions);
    }

    /** Skill 可删除 ⇔ 无任何已发布版本。 */
    public static boolean canDeleteSkill(Collection<SkillVersion> versions) {
        return !hasPublishedVersion(versions);
    }

    /** 版本可发布：DRAFT（首次上线）或 OFFLINE（重新上线）。 */
    public static boolean canPublish(SkillVersionStatus status) {
        return status == SkillVersionStatus.DRAFT || status == SkillVersionStatus.OFFLINE;
    }

    /** 版本可下线 ⇔ PUBLISHED。 */
    public static boolean canOffline(SkillVersionStatus status) {
        return status == SkillVersionStatus.PUBLISHED;
    }

    /** 版本可删除 ⇔ 非 PUBLISHED。 */
    public static boolean canDeleteVersion(SkillVersionStatus status) {
        return status != SkillVersionStatus.PUBLISHED;
    }

    /** 可编辑草稿：最新版本号的 DRAFT；不存在返回 empty（调用方新建）。 */
    public static Optional<SkillVersion> latestDraft(Collection<SkillVersion> versions) {
        return versions.stream()
                .filter(v -> v.getStatus() == SkillVersionStatus.DRAFT)
                .max(Comparator.comparing(SkillVersion::getVersion));
    }
}
