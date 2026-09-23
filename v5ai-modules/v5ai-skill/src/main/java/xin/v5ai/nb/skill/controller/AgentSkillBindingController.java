package xin.v5ai.nb.skill.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.skill.domain.bo.SkillBindBo;
import xin.v5ai.nb.skill.service.ISkillService;

import java.util.List;

/**
 * AgentDTO 绑定 Skill API。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agents/{agentKey}/skill-bindings")
public class AgentSkillBindingController extends BaseController {

    private final ISkillService skillService;

    @SaCheckPermission("agent:agent:edit")
    @PostMapping
    public R<Void> bindSkills(@PathVariable("agentKey") String agentKey,
                              @RequestBody SkillBindBo request) {
        if (request == null || request.skillIds() == null) {
            throw new IllegalArgumentException("skillIds is required");
        }
        return toAjax(skillService.bindSkills(agentKey, request.skillIds()));
    }

    @SaCheckPermission("agent:agent:query")
    @GetMapping
    public R<List<Long>> getBindings(@PathVariable("agentKey") String agentKey) {
        return R.ok(skillService.getSkillBindings(agentKey));
    }
}
