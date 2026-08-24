package xin.v5ai.nb.skill.controller;

import lombok.RequiredArgsConstructor;
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
import xin.v5ai.nb.common.core.domain.OptionDTO;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.skill.domain.bo.SkillBo;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.service.ISkillService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Skill 管理 API：上传（zip 包）、版本发布、回滚、禁用与版本列表。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/skills")
public class SkillController extends BaseController {

    private final ISkillService skillService;

    @PostMapping(consumes = "multipart/form-data")
    @Log(title = "上传技能", businessType = BusinessType.INSERT)
    public R<SkillVo> uploadSkill(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description
    ) {
        try {
            var created = skillService.uploadSkill(file.getBytes(), description);
            return R.ok(created);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded skill package", exception);
        }
    }

    @GetMapping
    public R<PageResult<SkillVo>> listSkills(SkillBo bo, PageQuery pageQuery) {
        return R.ok(skillService.queryPageList(bo, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionDTO>> listSkillOptions() {
        return R.ok(skillService.queryOptionList());
    }

    @DeleteMapping("/{id}")
    @Log(title = "禁用技能", businessType = BusinessType.DISABLE)
    public R<Void> disableSkill(@PathVariable("id") Long id) {
        skillService.disableSkill(id);
        return R.ok();
    }

    @GetMapping("/{id}/versions")
    public R<PageResult<SkillVersionVo>> listVersions(@PathVariable("id") Long id, PageQuery pageQuery) {
        var list = skillService.listVersions(id);
        return R.ok(PageResult.build(list, (long) list.size()));
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    @Log(title = "发布技能", businessType = BusinessType.PUBLISH)
    public R<SkillVo> publishVersion(@PathVariable("id") Long id,
                                     @PathVariable("versionId") Long versionId) {
        return R.ok(skillService.publishVersion(id, versionId));
    }

    @PostMapping("/{id}/rollback")
    @Log(title = "回滚技能", businessType = BusinessType.UPDATE)
    public R<SkillVo> rollback(@PathVariable("id") Long id,
                               @RequestBody RollbackRequest request) {
        if (request == null || request.versionId() == null) {
            throw new IllegalArgumentException("versionId is required");
        }
        return R.ok(skillService.rollback(id, request.versionId()));
    }
}
