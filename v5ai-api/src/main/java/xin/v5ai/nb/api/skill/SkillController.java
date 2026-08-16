package xin.v5ai.nb.api.skill;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xin.v5ai.nb.common.platform.AuditRecorder;
import xin.v5ai.nb.skill.SkillManagementService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Skill 管理 API：上传（zip 包）、版本发布、回滚、禁用与版本列表。
 */
@RestController
@RequestMapping("/api/admin/skills")
public class SkillController {
    private final SkillManagementService skillManagementService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public SkillController(SkillManagementService skillManagementService) {
        this.skillManagementService = skillManagementService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public SkillResponse uploadSkill(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description
    ) {
        try {
            var created = SkillResponse.from(skillManagementService.uploadSkill(file.getBytes(), description));
            audit("CREATE", "skill", String.valueOf(created.id()), "upload skill " + created.name());
            return created;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded skill package", exception);
        }
    }

    @GetMapping
    public List<SkillResponse> listSkills() {
        return skillManagementService.listSkills().stream()
                .map(SkillResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    public void disableSkill(@PathVariable("id") Long id) {
        skillManagementService.disableSkill(id);
        audit("DISABLE", "skill", String.valueOf(id), "disable skill");
    }

    @GetMapping("/{id}/versions")
    public List<SkillVersionResponse> listVersions(@PathVariable("id") Long id) {
        return skillManagementService.listVersions(id).stream()
                .map(SkillVersionResponse::from)
                .toList();
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    public SkillResponse publishVersion(@PathVariable("id") Long id,
                                        @PathVariable("versionId") Long versionId) {
        audit("PUBLISH", "skill", String.valueOf(id), "publish skill version " + versionId);
        return SkillResponse.from(skillManagementService.publishVersion(id, versionId));
    }

    @PostMapping("/{id}/rollback")
    public SkillResponse rollback(@PathVariable("id") Long id,
                                  @RequestBody RollbackRequest request) {
        if (request.versionId() == null) {
            throw new IllegalArgumentException("versionId is required");
        }
        audit("ROLLBACK", "skill", String.valueOf(id), "rollback skill to version " + request.versionId());
        return SkillResponse.from(skillManagementService.rollback(id, request.versionId()));
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
