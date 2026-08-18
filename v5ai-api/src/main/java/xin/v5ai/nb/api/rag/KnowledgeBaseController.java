package xin.v5ai.nb.api.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.rag.KnowledgeManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeManagementService knowledgeManagementService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public KnowledgeBaseController(KnowledgeManagementService knowledgeManagementService) {
        this.knowledgeManagementService = knowledgeManagementService;
    }

    @PostMapping
    public R<KnowledgeBaseResponse> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        var created = KnowledgeBaseResponse.from(
                knowledgeManagementService.createKnowledgeBase(request.name(), request.description()));
        audit("CREATE", "knowledge-base", String.valueOf(created.id()), "create knowledge base " + created.name());
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<KnowledgeBaseResponse>> listKnowledgeBases(PageQuery pageQuery) {
        var list = knowledgeManagementService.listKnowledgeBases().stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @DeleteMapping("/{id}")
    public R<Void> disableKnowledgeBase(@PathVariable("id") Long id) {
        knowledgeManagementService.disableKnowledgeBase(id);
        audit("DISABLE", "knowledge-base", String.valueOf(id), "disable knowledge base");
        return R.ok();
    }

    @GetMapping("/{kbId}/tasks")
    public R<PageResult<TaskResponse>> listTasks(@PathVariable("kbId") Long kbId, PageQuery pageQuery) {
        var list = knowledgeManagementService.listTasks(kbId).stream()
                .map(TaskResponse::from)
                .toList();
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
