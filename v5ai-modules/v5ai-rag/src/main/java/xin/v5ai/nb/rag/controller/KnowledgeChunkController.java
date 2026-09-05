package xin.v5ai.nb.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.domain.bo.KbChunkAddBo;
import xin.v5ai.nb.rag.domain.bo.KbChunkEditBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeChunkVo;
import xin.v5ai.nb.rag.service.IKnowledgeChunkAdminService;

/**
 * 知识库切片管理 API：分页浏览/筛选、手工新增（选文档）、编辑（重嵌）与删除。
 * <p>
 * 路径均挂在知识库下，服务端校验切片与文档归属。
 *
 * @author ZYW
 * @since 2026-09-06
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/knowledge-bases/{kbId}/chunks")
public class KnowledgeChunkController extends BaseController {

    private final IKnowledgeChunkAdminService chunkAdminService;

    @GetMapping
    public R<PageResult<KnowledgeChunkVo>> listChunks(
            @PathVariable("kbId") Long kbId,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "chunkId", required = false) Long chunkId,
            @RequestParam(value = "content", required = false) String content,
            PageQuery pageQuery
    ) {
        return R.ok(chunkAdminService.listChunks(kbId, documentId, chunkId, content, pageQuery));
    }

    @PostMapping
    @Log(title = "新增切片", businessType = BusinessType.INSERT)
    public R<KnowledgeChunkVo> addChunk(@PathVariable("kbId") Long kbId,
                                        @RequestBody KbChunkAddBo bo) {
        return R.ok(chunkAdminService.addChunk(kbId, bo));
    }

    @PutMapping("/{chunkId}")
    @Log(title = "编辑切片", businessType = BusinessType.UPDATE)
    public R<KnowledgeChunkVo> updateChunk(@PathVariable("kbId") Long kbId,
                                           @PathVariable("chunkId") Long chunkId,
                                           @RequestBody KbChunkEditBo bo) {
        return R.ok(chunkAdminService.updateChunk(kbId, chunkId, bo));
    }

    @DeleteMapping("/{chunkId}")
    @Log(title = "删除切片", businessType = BusinessType.DELETE)
    public R<Void> deleteChunk(@PathVariable("kbId") Long kbId,
                               @PathVariable("chunkId") Long chunkId) {
        chunkAdminService.deleteChunk(kbId, chunkId);
        return R.ok();
    }
}
