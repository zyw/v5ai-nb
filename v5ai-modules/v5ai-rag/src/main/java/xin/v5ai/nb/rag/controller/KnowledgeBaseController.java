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
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.util.List;

/**
 * 知识库管理 API：创建、编辑、分页查询、下拉选项、禁用与任务查询。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController extends BaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @Log(title = "新建知识库", businessType = BusinessType.INSERT)
    public R<KnowledgeBaseVo> createKnowledgeBase(@Validated(AddGroup.class) @RequestBody KnowledgeBaseBo bo) {
        return R.ok(knowledgeBaseService.createKnowledgeBase(bo));
    }

    @PutMapping
    @Log(title = "编辑知识库", businessType = BusinessType.UPDATE)
    public R<KnowledgeBaseVo> updateKnowledgeBase(@Validated(EditGroup.class) @RequestBody KnowledgeBaseBo bo) {
        return R.ok(knowledgeBaseService.updateKnowledgeBase(bo));
    }

    @GetMapping
    public R<PageResult<KnowledgeBaseVo>> listKnowledgeBases(KnowledgeBaseBo bo, PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.queryPageList(bo, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionDTO>> listKnowledgeBaseOptions() {
        return R.ok(knowledgeBaseService.queryOptionList());
    }

    @GetMapping("/{id}")
    public R<KnowledgeBaseVo> getKnowledgeBaseDetail(@PathVariable("id") Long id) {
        return R.ok(knowledgeBaseService.getKnowledgeBaseDetail(id));
    }

    @DeleteMapping("/{id}")
    @Log(title = "禁用知识库", businessType = BusinessType.DISABLE)
    public R<Void> disableKnowledgeBase(@PathVariable("id") Long id) {
        knowledgeBaseService.disableKnowledgeBase(id);
        return R.ok();
    }

    @GetMapping("/{kbId}/tasks")
    public R<PageResult<KnowledgeTaskVo>> listTasks(@PathVariable("kbId") Long kbId, PageQuery pageQuery) {
        var list = knowledgeBaseService.listTasks(kbId);
//      TODO PageUtils  return R.ok(PageUtils.page(list, pageQuery));
        return R.ok();
    }
}
