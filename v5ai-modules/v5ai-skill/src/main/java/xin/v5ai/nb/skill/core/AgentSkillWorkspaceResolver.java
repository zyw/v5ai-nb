package xin.v5ai.nb.skill.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;
import xin.v5ai.nb.common.agentscope.core.service.AgentSkillBindingService;
import xin.v5ai.nb.common.agentscope.core.service.SkillFileService;
import xin.v5ai.nb.common.agentscope.core.service.SkillService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 运行时 Skill 解析器实现：读取 Agent 绑定的 ACTIVE Skill 的当前发布版本文件。
 * 单个 Skill 解析失败时跳过，不影响整体运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSkillWorkspaceResolver implements SkillWorkspaceResolver {

    private final AgentSkillBindingService bindingService;
    private final SkillService skillService;
    private final SkillFileService fileService;

    @Override
    public List<ResolvedSkill> resolveSkills(Agent agent) {
        var bindings = bindingService.selectList(agent.agentKey());
        if (bindings.isEmpty()) {
            return List.of();
        }
        var resolved = new ArrayList<ResolvedSkill>();
        for (var binding : bindings) {
            resolveOne(agent, binding.getSkillId(), resolved);
        }
        return resolved;
    }

    private void resolveOne(Agent agent, Long skillId, List<ResolvedSkill> resolved) {
        var skill = skillService.selectById(skillId);
        if (skill == null || !"ACTIVE".equals(skill.getStatus()) || skill.getCurrentVersionId() == null) {
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
            resolved.add(new ResolvedSkill(skill.getName(), skill.getDescription(), files, skill.getCurrentVersionId()));
        } catch (Exception exception) {
            log.warn("failed to resolve skill '{}': {}", skill.getName(), exception.getMessage());
        }
    }
}
