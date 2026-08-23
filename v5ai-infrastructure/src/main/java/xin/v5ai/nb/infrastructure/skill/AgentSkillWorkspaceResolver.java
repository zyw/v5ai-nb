package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;

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

    private final AgentSkillBindingMapper bindingMapper;
    private final SkillMapper skillMapper;
    private final SkillFileMapper fileMapper;

    public AgentSkillWorkspaceResolver(
            AgentSkillBindingMapper bindingMapper,
            SkillMapper skillMapper,
            SkillFileMapper fileMapper
    ) {
        this.bindingMapper = bindingMapper;
        this.skillMapper = skillMapper;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<ResolvedSkill> resolveSkills(Agent application) {
        var bindings = bindingMapper.selectList(new LambdaQueryWrapper<AgentSkillBinding>()
                .eq(AgentSkillBinding::getAgentKey, application.agentKey()));
        if (bindings.isEmpty()) {
            return List.of();
        }
        var resolved = new ArrayList<ResolvedSkill>();
        for (var binding : bindings) {
            resolveOne(application, binding.getSkillId(), resolved);
        }
        return resolved;
    }

    private void resolveOne(Agent application, Long skillId, List<ResolvedSkill> resolved) {
        var skill = skillMapper.selectById(skillId);
        if (skill == null || !"ACTIVE".equals(skill.getStatus()) || skill.getCurrentVersionId() == null) {
            log.debug("skill {} missing/disabled/unpublished, skipped for app {}", skillId, application.agentKey());
            return;
        }
        try {
            var files = new LinkedHashMap<String, String>();
            var rows = fileMapper.selectList(new LambdaQueryWrapper<SkillFile>()
                    .eq(SkillFile::getVersionId, skill.getCurrentVersionId()));
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
