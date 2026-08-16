package xin.v5ai.nb.infrastructure.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.skill.AgentSkillBindingRepository;
import xin.v5ai.nb.skill.ResolvedSkill;
import xin.v5ai.nb.skill.SkillFile;
import xin.v5ai.nb.skill.SkillFileRepository;
import xin.v5ai.nb.skill.SkillRepository;
import xin.v5ai.nb.skill.SkillWorkspaceResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 运行时 Skill 解析器实现：读取 Agent 绑定的 ACTIVE Skill 的当前发布版本文件。
 * 单个 Skill 解析失败时跳过，不影响整体运行。
 */
@Component
public class AgentSkillWorkspaceResolver implements SkillWorkspaceResolver {
    private static final Logger log = LoggerFactory.getLogger(AgentSkillWorkspaceResolver.class);

    private final AgentSkillBindingRepository bindingRepository;
    private final SkillRepository skillRepository;
    private final SkillFileRepository fileRepository;

    public AgentSkillWorkspaceResolver(
            AgentSkillBindingRepository bindingRepository,
            SkillRepository skillRepository,
            SkillFileRepository fileRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.skillRepository = skillRepository;
        this.fileRepository = fileRepository;
    }

    @Override
    public List<ResolvedSkill> resolveSkills(Agent application) {
        var skillIds = bindingRepository.findByAgentKey(application.agentKey());
        if (skillIds.isEmpty()) {
            return List.of();
        }
        var resolved = new ArrayList<ResolvedSkill>();
        for (Long skillId : skillIds) {
            resolveOne(application, skillId, resolved);
        }
        return resolved;
    }

    private void resolveOne(Agent application, Long skillId, List<ResolvedSkill> resolved) {
        var skill = skillRepository.findById(skillId);
        if (skill == null || !skill.isActive() || skill.currentVersionId() == null) {
            log.debug("skill {} missing/disabled/unpublished, skipped for app {}", skillId, application.agentKey());
            return;
        }
        try {
            var files = new LinkedHashMap<String, String>();
            for (SkillFile file : fileRepository.findByVersionId(skill.currentVersionId())) {
                files.put(file.path(), file.content());
            }
            if (files.isEmpty()) {
                log.warn("skill '{}' has no files in current version, skipped", skill.name());
                return;
            }
            resolved.add(new ResolvedSkill(skill.name(), skill.description(), files, skill.currentVersionId()));
        } catch (Exception exception) {
            log.warn("failed to resolve skill '{}': {}", skill.name(), exception.getMessage());
        }
    }
}
