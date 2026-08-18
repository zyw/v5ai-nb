package xin.v5ai.nb.api.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
    public KnowledgeBaseResponse createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        var created = KnowledgeBaseResponse.from(
                knowledgeManagementService.createKnowledgeBase(request.name(), request.description()));
        audit("CREATE", "knowledge-base", String.valueOf(created.id()), "create knowledge base " + created.name());
        return created;
    }

    @GetMapping
    public List<KnowledgeBaseResponse> listKnowledgeBases() {
        return knowledgeManagementService.listKnowledgeBases().stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    public void disableKnowledgeBase(@PathVariable("id") Long id) {
        knowledgeManagementService.disableKnowledgeBase(id);
        audit("DISABLE", "knowledge-base", String.valueOf(id), "disable knowledge base");
    }

    @GetMapping("/{kbId}/tasks")
    public List<TaskResponse> listTasks(@PathVariable("kbId") Long kbId) {
        return knowledgeManagementService.listTasks(kbId).stream()
                .map(TaskResponse::from)
                .toList();
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
