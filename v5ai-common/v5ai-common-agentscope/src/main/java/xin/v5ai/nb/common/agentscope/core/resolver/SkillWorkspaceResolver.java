package xin.v5ai.nb.common.agentscope.core.resolver;

import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;

import java.util.Collection;
import java.util.List;

/**
 * 运行时 Skill 解析器：根据已发布 AgentDTO 的绑定配置，解析出本次运行要注入的
 * 已发布 Skill 集合。
 *
 * 约定：
 * - 只解析绑定的、处于 ACTIVE 状态且已发布（有当前版本）的 Skill；
 * - 调用方显式禁用的 Skill 直接跳过（在读取版本文件之前跳过）；
 * - 注入的是当前发布版本的不可变文件内容；
 * - 单个 Skill 解析失败时跳过，不中断整个运行。
 */
public interface SkillWorkspaceResolver {

    /**
     * 解析 AgentDTO 绑定的 Skill。
     *
     * @param agent             已发布的 AgentDTO
     * @param disabledSkillIds  本次运行要收窄掉的 Skill（只能减少绑定，不能增加）；
     *                          空集合表示不额外收窄
     * @return 本次运行可注入的 Skill（可能为空列表）
     */
    List<ResolvedSkill> resolveSkills(AgentDTO agent, Collection<Long> disabledSkillIds);

    /**
     * 不做额外收窄的便捷重载。
     */
    default List<ResolvedSkill> resolveSkills(AgentDTO agent) {
        return resolveSkills(agent, List.of());
    }
}
