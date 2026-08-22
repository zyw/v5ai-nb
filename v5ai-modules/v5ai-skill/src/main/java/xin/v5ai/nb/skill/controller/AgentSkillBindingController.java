package xin.v5ai.nb.skill.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.skill.service.ISkillService;

import java.util.List;

/**
 * Agent 绑定 Skill API。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agents/{agentKey}/skill-bindings")
public class AgentSkillBindingController {

    private final ISkillService skillService;

    @PostMapping
    public R<Void> bindSkills(@PathVariable("agentKey") String agentKey,
                              @RequestBody SkillBindRequest request) {
        if (request == null || request.skillIds() == null) {
            throw new IllegalArgumentException("skillIds is required");
        }
        skillService.bindSkills(agentKey, request.skillIds());
        return R.ok();
    }

    @GetMapping
    public R<List<Long>> getBindings(@PathVariable("agentKey") String agentKey) {
        return R.ok(skillService.getSkillBindings(agentKey));
    }
}
