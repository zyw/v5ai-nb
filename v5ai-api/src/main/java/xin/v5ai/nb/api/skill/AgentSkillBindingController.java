package xin.v5ai.nb.api.skill;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.skill.SkillManagementService;

import java.util.List;

/**
 * Agent 绑定 Skill API。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentKey}/skill-bindings")
public class AgentSkillBindingController {
    private final SkillManagementService skillManagementService;

    public AgentSkillBindingController(SkillManagementService skillManagementService) {
        this.skillManagementService = skillManagementService;
    }

    @PostMapping
    public void bindSkills(@PathVariable("agentKey") String agentKey,
                           @RequestBody SkillBindRequest request) {
        skillManagementService.bindSkills(agentKey, request.skillIds());
    }

    @GetMapping
    public List<Long> getBindings(@PathVariable("agentKey") String agentKey) {
        return skillManagementService.getSkillBindings(agentKey);
    }
}
