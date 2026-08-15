package xin.v5ai.nb.skill;

import xin.v5ai.nb.application.Application;

import java.util.List;

/**
 * 运行时 Skill 解析器：根据已发布 Application 的绑定配置，解析出本次运行要注入的
 * 已发布 Skill 集合。
 *
 * 约定：
 * - 只解析绑定的、处于 ACTIVE 状态且已发布（有当前版本）的 Skill；
 * - 注入的是当前发布版本的不可变文件内容；
 * - 单个 Skill 解析失败时跳过，不中断整个运行。
 */
public interface SkillWorkspaceResolver {

    /**
     * 解析 Application 绑定的 Skill。
     *
     * @param application 已发布的 Application
     * @return 本次运行可注入的 Skill（可能为空列表）
     */
    List<ResolvedSkill> resolveSkills(Application application);
}
