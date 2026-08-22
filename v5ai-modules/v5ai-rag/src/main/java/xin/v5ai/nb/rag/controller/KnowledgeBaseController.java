package xin.v5ai.nb.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.OptionDTO;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.util.List;

/**
 * 知识库管理 API：创建、分页查询、下拉选项、禁用与任务查询。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController extends BaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    @PostMapping
    public R<KnowledgeBaseVo> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        var bo = new KnowledgeBaseBo();
        bo.setName(request == null ? null : request.name());
        bo.setDescription(request == null ? null : request.description());
        var created = knowledgeBaseService.createKnowledgeBase(bo);
        audit("CREATE", "knowledge-base", String.valueOf(created.getId()), "create knowledge base " + created.getName());
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<KnowledgeBaseVo>> listKnowledgeBases(KnowledgeBaseBo bo, PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.queryPageList(bo, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionDTO>> listKnowledgeBaseOptions() {
        return R.ok(knowledgeBaseService.queryOptionList());
    }

    @DeleteMapping("/{id}")
    public R<Void> disableKnowledgeBase(@PathVariable("id") Long id) {
        knowledgeBaseService.disableKnowledgeBase(id);
        audit("DISABLE", "knowledge-base", String.valueOf(id), "disable knowledge base");
        return R.ok();
    }

    @GetMapping("/{kbId}/tasks")
    public R<PageResult<KnowledgeTaskVo>> listTasks(@PathVariable("kbId") Long kbId, PageQuery pageQuery) {
        var list = knowledgeBaseService.listTasks(kbId);
        return R.ok(PageUtils.page(list, pageQuery));
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
