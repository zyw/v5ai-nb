package xin.v5ai.nb.api.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xin.v5ai.nb.api.common.OptionResponse;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.skill.SkillManagementService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Skill 管理 API：上传（zip 包）、版本发布、回滚、禁用与版本列表。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/skills")
public class SkillController {

    private final SkillManagementService skillManagementService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    @PostMapping(consumes = "multipart/form-data")
    public R<SkillResponse> uploadSkill(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description
    ) {
        try {
            var created = SkillResponse.from(skillManagementService.uploadSkill(file.getBytes(), description));
            audit("CREATE", "skill", String.valueOf(created.id()), "upload skill " + created.name());
            return R.ok(created);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded skill package", exception);
        }
    }

    @GetMapping
    public R<PageResult<SkillResponse>> listSkills(PageQuery pageQuery) {
        var list = skillManagementService.listSkills().stream()
                .map(SkillResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionResponse>> listSkillOptions() {
        return R.ok(skillManagementService.listSkillOptions().stream()
                .map(skill -> new OptionResponse(skill.id(), skill.name() + " (v" + skill.currentVersion() + ")"))
                .toList());
    }

    @DeleteMapping("/{id}")
    public R<Void> disableSkill(@PathVariable("id") Long id) {
        skillManagementService.disableSkill(id);
        audit("DISABLE", "skill", String.valueOf(id), "disable skill");
        return R.ok();
    }

    @GetMapping("/{id}/versions")
    public R<PageResult<SkillVersionResponse>> listVersions(@PathVariable("id") Long id, PageQuery pageQuery) {
        var list = skillManagementService.listVersions(id).stream()
                .map(SkillVersionResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    public R<SkillResponse> publishVersion(@PathVariable("id") Long id,
                                        @PathVariable("versionId") Long versionId) {
        audit("PUBLISH", "skill", String.valueOf(id), "publish skill version " + versionId);
        return R.ok(SkillResponse.from(skillManagementService.publishVersion(id, versionId)));
    }

    @PostMapping("/{id}/rollback")
    public R<SkillResponse> rollback(@PathVariable("id") Long id,
                                  @RequestBody RollbackRequest request) {
        if (request.versionId() == null) {
            throw new IllegalArgumentException("versionId is required");
        }
        audit("ROLLBACK", "skill", String.valueOf(id), "rollback skill to version " + request.versionId());
        return R.ok(SkillResponse.from(skillManagementService.rollback(id, request.versionId())));
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = LoginHelper.getUsername();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
