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
 * Application 绑定 Skill API。
 */
@RestController
@RequestMapping("/api/admin/applications/{appKey}/skill-bindings")
public class ApplicationSkillBindingController {
    private final SkillManagementService skillManagementService;

    public ApplicationSkillBindingController(SkillManagementService skillManagementService) {
        this.skillManagementService = skillManagementService;
    }

    @PostMapping
    public void bindSkills(@PathVariable("appKey") String appKey,
                           @RequestBody SkillBindRequest request) {
        skillManagementService.bindSkills(appKey, request.skillIds());
    }

    @GetMapping
    public List<Long> getBindings(@PathVariable("appKey") String appKey) {
        return skillManagementService.getSkillBindings(appKey);
    }
}
