package xin.v5ai.nb.skill.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.skill.domain.bo.*;
import xin.v5ai.nb.skill.domain.vo.SkillEditorVo;
import xin.v5ai.nb.skill.domain.vo.SkillUsageVo;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.service.ISkillService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Skill 管理 API：上传（zip 包）、在线新建、文件在线编辑、版本发布、回滚、禁用与版本列表。
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

    @PostMapping("/online")
    @Log(title = "在线新建技能", businessType = BusinessType.INSERT)
    public R<SkillVo> createOnline(@RequestBody CreateSkillBo request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return R.ok(skillService.createSkillOnline(request.name(), request.description(), request.versionDescription()));
    }

    @GetMapping("/{id}/editor")
    public R<SkillEditorVo> getEditor(@PathVariable("id") Long id) {
        return R.ok(skillService.getSkillEditor(id));
    }

    @PostMapping("/{id}/files")
    @Log(title = "新建技能文件", businessType = BusinessType.INSERT)
    public R<Void> createFile(@PathVariable("id") Long id, @RequestBody SkillFileBo request) {
        requireFileRequest(request);
        skillService.createSkillFile(id, request.filePath(), request.content());
        return R.ok();
    }

    @PutMapping("/{id}/files")
    @Log(title = "更新技能文件", businessType = BusinessType.UPDATE)
    public R<Void> updateFile(@PathVariable("id") Long id, @RequestBody SkillFileBo request) {
        requireFileRequest(request);
        skillService.updateSkillFile(id, request.filePath(), request.content());
        return R.ok();
    }

    @DeleteMapping("/{id}/files")
    @Log(title = "删除技能文件", businessType = BusinessType.DELETE)
    public R<Void> deleteFile(@PathVariable("id") Long id, @RequestParam("path") String path) {
        skillService.deleteSkillFile(id, path);
        return R.ok();
    }

    @PostMapping("/{id}/ai/generate")
    @Log(title = "AI 生成技能", businessType = BusinessType.UPDATE)
    public R<String> aiGenerate(@PathVariable("id") Long id, @RequestBody AiGenerateBo request) {
        if (request == null || request.modelId() == null
                || request.requirement() == null || request.requirement().isBlank()) {
            throw new IllegalArgumentException("modelId and requirement are required");
        }
        return R.ok("Success Generated Markdown Content",skillService.generateSkillMd(id, request.modelId(), request.requirement().trim()));
    }

    @PostMapping("/{id}/files/ai/optimize")
    @Log(title = "AI 优化技能文件", businessType = BusinessType.UPDATE)
    public R<String> aiOptimize(@PathVariable("id") Long id, @RequestBody AiOptimizeBo request) {
        if (request == null || request.modelId() == null
                || request.filePath() == null || request.filePath().isBlank()
                || request.requirement() == null || request.requirement().isBlank()) {
            throw new IllegalArgumentException("modelId, filePath and requirement are required");
        }
        return R.ok(skillService.optimizeSkillFile(id, request.modelId(), request.filePath(),
                request.requirement().trim(), request.direction()));
    }

    private static void requireFileRequest(SkillFileBo request) {
        if (request == null || request.filePath() == null || request.filePath().isBlank()) {
            throw new IllegalArgumentException("filePath is required");
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

    @PutMapping("/{id}/disable")
    @Log(title = "禁用技能", businessType = BusinessType.DISABLE)
    public R<Void> disableSkill(@PathVariable("id") Long id) {
        return toAjax(skillService.disableSkill(id));
    }

    @PutMapping("/{id}/enable")
    @Log(title = "启用技能", businessType = BusinessType.ENABLE)
    public R<Void> enableSkill(@PathVariable("id") Long id) {
        return toAjax(skillService.enableSkill(id));
    }

    @DeleteMapping("/{id}")
    @Log(title = "删除技能", businessType = BusinessType.DELETE)
    public R<Void> deleteSkill(@PathVariable("id") Long id) {
        return toAjax(skillService.deleteSkill(id));
    }

    @DeleteMapping("/{id}/versions/{versionId}")
    @Log(title = "删除技能版本", businessType = BusinessType.DELETE)
    public R<Void> deleteSkillVersion(@PathVariable("id") Long id,
                                      @PathVariable("versionId") Long versionId) {
        return toAjax(skillService.deleteSkillVersion(id, versionId));
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

    @PostMapping("/{id}/versions/{versionId}/offline")
    @Log(title = "下线技能版本", businessType = BusinessType.UPDATE)
    public R<SkillVo> offlineVersion(@PathVariable("id") Long id,
                                     @PathVariable("versionId") Long versionId) {
        return R.ok(skillService.offlineVersion(id, versionId));
    }

    @GetMapping("/{id}/usage")
    public R<SkillUsageVo> getUsage(@PathVariable("id") Long id) {
        return R.ok(skillService.getSkillUsage(id));
    }

    @PostMapping("/{id}/rollback")
    @Log(title = "回滚技能", businessType = BusinessType.UPDATE)
    public R<SkillVo> rollback(@PathVariable("id") Long id,
                               @RequestBody RollbackBo request) {
        if (request == null || request.versionId() == null) {
            throw new IllegalArgumentException("versionId is required");
        }
        return R.ok(skillService.rollback(id, request.versionId()));
    }
}
