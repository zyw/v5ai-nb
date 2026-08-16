package xin.v5ai.nb.workflow;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Workflow 管理服务：草稿编辑、发布（DAG 校验 + 版本快照）、禁用与运行记录查询。
 * 执行逻辑见 {@link WorkflowEngine}。
 */
@Service
public class WorkflowManagementService {
    private final WorkflowRepository repository;
    private final WorkflowRunRepository runRepository;

    public WorkflowManagementService(WorkflowRepository repository, WorkflowRunRepository runRepository) {
        this.repository = repository;
        this.runRepository = runRepository;
    }

    public Workflow create(String workflowKey, String name, String description) {
        if (workflowKey == null || workflowKey.isBlank()) {
            throw new IllegalArgumentException("workflowKey is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (repository.findByKey(workflowKey) != null) {
            throw new IllegalArgumentException("workflow already exists: " + workflowKey);
        }
        var now = Instant.now();
        return repository.save(new Workflow(null, workflowKey, name, description,
                WorkflowStatus.DRAFT, null, null, null, null, now, now));
    }

    public List<Workflow> list() {
        return repository.list();
    }

    public Workflow get(String workflowKey) {
        return requireWorkflow(workflowKey);
    }

    public Workflow update(String workflowKey, String name, String description, WorkflowDefinition draft) {
        var existing = requireActive(workflowKey);
        String nextName = name == null || name.isBlank() ? existing.name() : name;
        String nextDescription = description == null ? existing.description() : description;
        WorkflowDefinition nextDraft = draft == null ? existing.draftDefinition() : draft;
        return repository.update(existing.withDraft(nextName, nextDescription, nextDraft));
    }

    public Workflow publish(String workflowKey) {
        var existing = requireActive(workflowKey);
        if (existing.draftDefinition() == null) {
            throw new IllegalArgumentException("workflow has no draft definition to publish: " + workflowKey);
        }
        WorkflowDefinitionValidator.validate(existing.draftDefinition());
        long version = existing.publishedVersion() == null ? 1 : existing.publishedVersion() + 1;
        return repository.update(existing.withPublished(existing.draftDefinition(), version, Instant.now()));
    }

    public void disable(String workflowKey) {
        var existing = requireWorkflow(workflowKey);
        repository.disable(existing.id());
    }

    public List<WorkflowRun> listRuns(String workflowKey) {
        requireWorkflow(workflowKey);
        return runRepository.listRuns(workflowKey);
    }

    public WorkflowRun getRun(String runId) {
        var run = runRepository.findRun(runId);
        if (run == null) {
            throw new IllegalArgumentException("workflow run does not exist: " + runId);
        }
        return run;
    }

    public List<WorkflowNodeRun> getNodeRuns(String runId) {
        return runRepository.findNodeRuns(runId);
    }

    private Workflow requireWorkflow(String workflowKey) {
        var workflow = repository.findByKey(workflowKey);
        if (workflow == null) {
            throw new IllegalArgumentException("workflow does not exist: " + workflowKey);
        }
        return workflow;
    }

    private Workflow requireActive(String workflowKey) {
        var workflow = requireWorkflow(workflowKey);
        if (!workflow.isActive()) {
            throw new IllegalArgumentException("workflow is disabled: " + workflowKey);
        }
        return workflow;
    }
}
