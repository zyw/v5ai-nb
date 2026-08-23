package xin.v5ai.nb.workflow.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.workflow.core.Workflow;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowRepository;
import xin.v5ai.nb.workflow.core.enums.WorkflowStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * {@link WorkflowRepository} 的 MyBatis-Plus 实现（供 {@code WorkflowEngine} 运行时读取）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
@RequiredArgsConstructor
public class MyBatisWorkflowRepository implements WorkflowRepository {

    private final WorkflowMapper workflowMapper;

    @Override
    public Workflow save(Workflow workflow) {
        var entity = toEntity(workflow);
        workflowMapper.insert(entity);
        return new Workflow(entity.getId(), workflow.workflowKey(), workflow.name(), workflow.description(),
                workflow.status(), workflow.draftDefinition(), workflow.publishedDefinition(),
                workflow.publishedVersion(), workflow.publishedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public Workflow update(Workflow workflow) {
        var entity = toEntity(workflow);
        workflowMapper.updateById(entity);
        return workflow;
    }

    @Override
    public Workflow findById(Long id) {
        return toDomain(workflowMapper.selectById(id));
    }

    @Override
    public Workflow findByKey(String workflowKey) {
        return toDomain(workflowMapper.selectOne(new LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.Workflow>()
                .eq(xin.v5ai.nb.workflow.domain.Workflow::getWorkflowKey, workflowKey)));
    }

    @Override
    public List<Workflow> list() {
        return workflowMapper.selectList(new LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.Workflow>()
                        .orderByAsc(xin.v5ai.nb.workflow.domain.Workflow::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void disable(Long id) {
        var update = new xin.v5ai.nb.workflow.domain.Workflow();
        update.setId(id);
        update.setStatus(WorkflowStatus.DISABLED.name());
        workflowMapper.updateById(update);
    }

    private xin.v5ai.nb.workflow.domain.Workflow toEntity(Workflow workflow) {
        var entity = new xin.v5ai.nb.workflow.domain.Workflow();
        entity.setId(workflow.id());
        entity.setWorkflowKey(workflow.workflowKey());
        entity.setName(workflow.name());
        entity.setDescription(workflow.description());
        entity.setStatus(workflow.status() == null ? null : workflow.status().name());
        entity.setDraftDefinitionJson(WorkflowJson.toJson(workflow.draftDefinition()));
        entity.setPublishedDefinitionJson(WorkflowJson.toJson(workflow.publishedDefinition()));
        entity.setPublishedVersion(workflow.publishedVersion());
        entity.setPublishedAt(workflow.publishedAt());
        return entity;
    }

    private Workflow toDomain(xin.v5ai.nb.workflow.domain.Workflow entity) {
        if (entity == null) {
            return null;
        }
        return new Workflow(entity.getId(), entity.getWorkflowKey(), entity.getName(), entity.getDescription(),
                entity.getStatus() == null ? null : WorkflowStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseDefinition(entity.getDraftDefinitionJson()),
                WorkflowJson.parseDefinition(entity.getPublishedDefinitionJson()),
                entity.getPublishedVersion(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

//    private static Instant toInstant(LocalDateTime value) {
//        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
//    }
//
//    private static LocalDateTime toLocalDateTime(Instant value) {
//        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
//    }
}
