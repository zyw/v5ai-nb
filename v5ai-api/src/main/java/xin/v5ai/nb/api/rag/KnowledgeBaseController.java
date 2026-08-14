package xin.v5ai.nb.api.rag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.rag.KnowledgeManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeManagementService knowledgeManagementService;

    public KnowledgeBaseController(KnowledgeManagementService knowledgeManagementService) {
        this.knowledgeManagementService = knowledgeManagementService;
    }

    @PostMapping
    public KnowledgeBaseResponse createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        return KnowledgeBaseResponse.from(knowledgeManagementService.createKnowledgeBase(request.name(), request.description()));
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
    }

    @GetMapping("/{kbId}/tasks")
    public List<TaskResponse> listTasks(@PathVariable("kbId") Long kbId) {
        return knowledgeManagementService.listTasks(kbId).stream()
                .map(TaskResponse::from)
                .toList();
    }
}
