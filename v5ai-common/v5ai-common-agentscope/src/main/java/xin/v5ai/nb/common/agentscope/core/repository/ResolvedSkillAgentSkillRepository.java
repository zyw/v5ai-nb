package xin.v5ai.nb.common.agentscope.core.repository;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import io.agentscope.core.skill.util.SkillUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台已发布 Skill 到 AgentScope {@link AgentSkillRepository} 的只读适配：
 * 以当前发布版本的不可变文件内容构建 {@link AgentSkill}，供 HarnessAgent 的
 * SkillRuntime 注入 `<available_skills>` 到系统提示。
 *
 * 安全边界：只暴露平台校验并发布的版本内容；单个 Skill 构建失败仅跳过。
 */
@Slf4j
public class ResolvedSkillAgentSkillRepository implements AgentSkillRepository {

    private final Map<String, AgentSkill> skills;
    private boolean writeable;

    public ResolvedSkillAgentSkillRepository(List<ResolvedSkill> resolvedSkills) {
        Map<String, AgentSkill> built = new LinkedHashMap<>();
        if (resolvedSkills != null) {
            for (ResolvedSkill resolved : resolvedSkills) {
                try {
                    var skill = toAgentSkill(resolved);
                    if (skill != null) {
                        built.put(skill.getName(), skill);
                    }
                } catch (Exception exception) {
                    log.warn("failed to build AgentSkill '{}': {}", resolved.skillName(), exception.getMessage());
                }
            }
        }
        this.skills = Map.copyOf(built);
    }

    private static AgentSkill toAgentSkill(ResolvedSkill resolved) {
        if (resolved.files() == null) {
            return null;
        }
        String skillMd = resolved.files().get("SKILL.md");
        if (skillMd == null || skillMd.isBlank()) {
            return null;
        }
        Map<String, String> resources = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolved.files().entrySet()) {
            if (!"SKILL.md".equals(entry.getKey())) {
                resources.put(entry.getKey(), entry.getValue());
            }
        }
        return SkillUtil.createFrom(skillMd, resources, "v5ai-db");
    }

    @Override
    public AgentSkill getSkill(String name) {
        return skills.get(name);
    }

    @Override
    public List<String> getAllSkillNames() {
        return List.copyOf(skills.keySet());
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        return false;
    }

    @Override
    public boolean delete(String skillName) {
        return false;
    }

    @Override
    public boolean skillExists(String skillName) {
        return skills.containsKey(skillName);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("v5ai-db", "published-skill-versions", false);
    }

    @Override
    public String getSource() {
        return "v5ai-db";
    }

    @Override
    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    @Override
    public boolean isWriteable() {
        return writeable;
    }
}
