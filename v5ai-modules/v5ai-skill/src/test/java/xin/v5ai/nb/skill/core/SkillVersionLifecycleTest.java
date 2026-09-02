package xin.v5ai.nb.skill.core;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.enums.SkillVersionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SkillVersionLifecycle} 纯规则单测：状态迁移合法性与守卫判定的完整裁决表。
 */
class SkillVersionLifecycleTest {

    private static SkillVersion version(long id, long number, SkillVersionStatus status) {
        var v = new SkillVersion();
        v.setId(id);
        v.setVersion(number);
        v.setStatus(status);
        return v;
    }

    @Test
    void publishAllowedForDraftAndOfflineOnly() {
        assertThat(SkillVersionLifecycle.canPublish(SkillVersionStatus.DRAFT)).isTrue();
        assertThat(SkillVersionLifecycle.canPublish(SkillVersionStatus.OFFLINE)).isTrue();
        assertThat(SkillVersionLifecycle.canPublish(SkillVersionStatus.PUBLISHED)).isFalse();
    }

    @Test
    void offlineAllowedForPublishedOnly() {
        assertThat(SkillVersionLifecycle.canOffline(SkillVersionStatus.PUBLISHED)).isTrue();
        assertThat(SkillVersionLifecycle.canOffline(SkillVersionStatus.DRAFT)).isFalse();
        assertThat(SkillVersionLifecycle.canOffline(SkillVersionStatus.OFFLINE)).isFalse();
    }

    @Test
    void deleteVersionAllowedExceptPublished() {
        assertThat(SkillVersionLifecycle.canDeleteVersion(SkillVersionStatus.DRAFT)).isTrue();
        assertThat(SkillVersionLifecycle.canDeleteVersion(SkillVersionStatus.OFFLINE)).isTrue();
        assertThat(SkillVersionLifecycle.canDeleteVersion(SkillVersionStatus.PUBLISHED)).isFalse();
    }

    @Test
    void skillGuardBlocksWhenAnyPublishedVersionExists() {
        // v1 PUBLISHED 非当前（current=null 的情形由 service 维护），Skill 仍不可删/禁
        var versions = List.of(
                version(1L, 1L, SkillVersionStatus.PUBLISHED),
                version(2L, 2L, SkillVersionStatus.OFFLINE));

        assertThat(SkillVersionLifecycle.hasPublishedVersion(versions)).isTrue();
        assertThat(SkillVersionLifecycle.canDeleteSkill(versions)).isFalse();
        assertThat(SkillVersionLifecycle.canDisableSkill(versions)).isFalse();
    }

    @Test
    void skillGuardAllowsWhenNoPublishedVersion() {
        var versions = List.of(
                version(1L, 1L, SkillVersionStatus.DRAFT),
                version(2L, 2L, SkillVersionStatus.OFFLINE));

        assertThat(SkillVersionLifecycle.hasPublishedVersion(versions)).isFalse();
        assertThat(SkillVersionLifecycle.canDeleteSkill(versions)).isTrue();
        assertThat(SkillVersionLifecycle.canDisableSkill(versions)).isTrue();
    }

    @Test
    void latestDraftPicksNewestDraftOnly() {
        var versions = List.of(
                version(1L, 1L, SkillVersionStatus.PUBLISHED),
                version(2L, 2L, SkillVersionStatus.OFFLINE),
                version(3L, 3L, SkillVersionStatus.DRAFT),
                version(4L, 4L, SkillVersionStatus.DRAFT));

        assertThat(SkillVersionLifecycle.latestDraft(versions)).get().extracting(SkillVersion::getId).isEqualTo(4L);
    }

    @Test
    void latestDraftEmptyWhenNoDraft() {
        var versions = List.of(
                version(1L, 1L, SkillVersionStatus.PUBLISHED),
                version(2L, 2L, SkillVersionStatus.OFFLINE));

        assertThat(SkillVersionLifecycle.latestDraft(versions)).isEmpty();
    }
}
