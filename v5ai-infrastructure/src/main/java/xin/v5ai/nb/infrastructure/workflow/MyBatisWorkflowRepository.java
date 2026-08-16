package xin.v5ai.nb.infrastructure.workflow;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.workflow.Workflow;
import xin.v5ai.nb.workflow.WorkflowJson;
import xin.v5ai.nb.workflow.WorkflowRepository;
import xin.v5ai.nb.workflow.WorkflowStatus;

import java.time.Instant;
import java.util.List;

@Repository
public class MyBatisWorkflowRepository implements WorkflowRepository {
    private final WorkflowGateway gateway;

    public MyBatisWorkflowRepository(WorkflowGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Workflow save(Workflow workflow) {
        var entity = toEntity(workflow);
        gateway.insertWorkflow(entity);
        return new Workflow(entity.getId(), workflow.workflowKey(), workflow.name(), workflow.description(),
                workflow.status(), workflow.draftDefinition(), workflow.publishedDefinition(),
                workflow.publishedVersion(), workflow.publishedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public Workflow update(Workflow workflow) {
        var entity = toEntity(workflow);
        entity.setUpdatedAt(Instant.now());
        gateway.updateWorkflow(entity);
        return workflow;
    }

    @Override
    public Workflow findById(Long id) {
        return toDomain(gateway.selectWorkflowById(id));
    }

    @Override
    public Workflow findByKey(String workflowKey) {
        return toDomain(gateway.selectWorkflowByKey(workflowKey));
    }

    @Override
    public List<Workflow> list() {
        return gateway.selectAllWorkflows().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void disable(Long id) {
        gateway.disableWorkflow(id);
    }

    private WorkflowEntity toEntity(Workflow workflow) {
        var entity = new WorkflowEntity();
        entity.setId(workflow.id());
        entity.setWorkflowKey(workflow.workflowKey());
        entity.setName(workflow.name());
        entity.setDescription(workflow.description());
        entity.setStatus(workflow.status() == null ? null : workflow.status().name());
        entity.setDraftDefinition(WorkflowJson.toJson(workflow.draftDefinition()));
        entity.setPublishedDefinition(WorkflowJson.toJson(workflow.publishedDefinition()));
        entity.setPublishedVersion(workflow.publishedVersion());
        entity.setPublishedAt(workflow.publishedAt());
        entity.setCreatedAt(workflow.createdAt());
        entity.setUpdatedAt(workflow.updatedAt());
        return entity;
    }

    private Workflow toDomain(WorkflowEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Workflow(entity.getId(), entity.getWorkflowKey(), entity.getName(), entity.getDescription(),
                entity.getStatus() == null ? null : WorkflowStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseDefinition(entity.getDraftDefinition()),
                WorkflowJson.parseDefinition(entity.getPublishedDefinition()),
                entity.getPublishedVersion(), entity.getPublishedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
