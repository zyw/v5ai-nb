package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.PlmResourceBo;
import xin.v5ai.nb.platform.domain.vo.PlmResourceVo;
import xin.v5ai.nb.platform.service.IPlmResourceService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * <p>
 * 通用资源存储前端控制器（/api/admin/resources）
 * </p>
 *
 * @author ZYW
 * @since 2026-09-02
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/resources")
public class PlmResourceController extends BaseController {

    private final IPlmResourceService resourceService;

    /**
     * 分页查询资源列表（文件名模糊 / 业务类型 / 创建时间区间）
     */
    @SaCheckPermission("platform:resource:list")
    @GetMapping
    public R<PageResult<PlmResourceVo>> list(PlmResourceBo bo, PageQuery pageQuery) {
        return R.ok(resourceService.queryPageList(bo, pageQuery));
    }

    /**
     * 查询资源详情
     */
    @SaCheckPermission("platform:resource:query")
    @GetMapping("/{id}")
    public R<PlmResourceVo> getInfo(@NotNull(message = "主键不能为空")
                                    @PathVariable("id") Long id) {
        return R.ok(resourceService.queryById(id));
    }

    /**
     * 上传文件（multipart/form-data：file + 可选 bizType/bizId；存储类型由配置 v5ai.storage.type 决定）
     */
    @SaCheckPermission("platform:resource:add")
    @Log(title = "上传资源", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public R<PlmResourceVo> upload(@RequestPart("file") MultipartFile file,
                                   @RequestParam(value = "bizType", required = false) String bizType,
                                   @RequestParam(value = "bizId", required = false) Long bizId) {
        try {
            return R.ok(resourceService.upload(
                    file.getOriginalFilename(), file.getBytes(), file.getContentType(),
                    bizType, bizId));
        } catch (IOException exception) {
            throw new UncheckedIOException("读取上传文件失败", exception);
        }
    }

    /**
     * 修改资源元数据（业务类型 / 关联业务ID）
     */
    @SaCheckPermission("platform:resource:edit")
    @Log(title = "修改资源", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody PlmResourceBo bo) {
        return toAjax(resourceService.updateByBo(bo));
    }

    /**
     * 批量删除资源（物理删除文件与记录）
     */
    @SaCheckPermission("platform:resource:remove")
    @Log(title = "删除资源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("ids") Long[] ids) {
        return toAjax(resourceService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 预览文件（inline，图片/PDF 等浏览器可直接查看）
     */
    @SaCheckPermission("platform:resource:query")
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable("id") Long id) {
        IPlmResourceService.ResourceContent content = resourceService.loadContent(id);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.bytes());
    }

    /**
     * 下载文件（attachment，保留原始文件名）
     */
    @SaCheckPermission("platform:resource:query")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id) {
        IPlmResourceService.ResourceContent content = resourceService.loadContent(id);
        String encoded = URLEncoder.encode(content.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .body(content.bytes());
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
