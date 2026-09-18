package xin.v5ai.nb.skill.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.skill.domain.enums.SkillStatus;
import xin.v5ai.nb.skill.service.IAgentSkillBindingService;
import xin.v5ai.nb.skill.service.ISkillFileService;
import xin.v5ai.nb.skill.service.ISkillService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 运行时 Skill 解析器实现：读取 AgentDTO 绑定的 ACTIVE Skill 的当前发布版本文件。
 * 单个 Skill 解析失败时跳过，不影响整体运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSkillWorkspaceResolver implements SkillWorkspaceResolver {

    private final IAgentSkillBindingService bindingService;
    private final ISkillService skillService;
    private final ISkillFileService fileService;

    @Override
    public List<ResolvedSkill> resolveSkills(AgentDTO agent, Collection<Long> disabledSkillIds) {
        var bindings = bindingService.selectList(agent.agentKey());
        if (bindings.isEmpty()) {
            return List.of();
        }
        var disabled = disabledSkillIds == null ? Set.<Long>of() : Set.copyOf(disabledSkillIds);
        var resolved = new ArrayList<ResolvedSkill>();
        for (var binding : bindings) {
            if (disabled.contains(binding.getSkillId())) {
                log.debug("skill {} disabled for this run, skipped for app {}", binding.getSkillId(), agent.agentKey());
                continue;
            }
            resolveOne(agent, binding.getSkillId(), resolved);
        }
        return resolved;
    }

    private void resolveOne(AgentDTO agent, Long skillId, List<ResolvedSkill> resolved) {
        var skill = skillService.getSkill(skillId);
        if (skill == null || !SkillStatus.ACTIVE.name().equals(skill.getStatus()) || skill.getCurrentVersionId() == null) {
            log.debug("skill {} missing/disabled/unpublished, skipped for app {}", skillId, agent.agentKey());
            return;
        }
        try {
            var files = new LinkedHashMap<String, String>();
            var rows = fileService.selectList(skill.getCurrentVersionId());
            for (var file : rows) {
                files.put(file.getFilePath(), file.getContent());
            }
            if (files.isEmpty()) {
                log.warn("skill '{}' has no files in current version, skipped", skill.getName());
                return;
            }
            resolved.add(new ResolvedSkill(skill.getId(), skill.getName(), skill.getDescription(), files,
                    skill.getCurrentVersionId()));
        } catch (Exception exception) {
            log.warn("failed to resolve skill '{}': {}", skill.getName(), exception.getMessage());
        }
    }
}
