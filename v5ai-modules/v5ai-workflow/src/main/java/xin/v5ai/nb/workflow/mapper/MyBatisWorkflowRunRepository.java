package xin.v5ai.nb.workflow.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowNodeRun;
import xin.v5ai.nb.workflow.core.WorkflowRun;
import xin.v5ai.nb.workflow.core.WorkflowRunRepository;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;

import java.time.Instant;
import java.util.List;

/**
 * {@link WorkflowRunRepository} 的 MyBatis-Plus 实现（供 {@code WorkflowEngine} 持久化运行记录）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
@RequiredArgsConstructor
public class MyBatisWorkflowRunRepository implements WorkflowRunRepository {

    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;

    @Override
    public void start(WorkflowRun run) {
        runMapper.insert(toEntity(run));
    }

    @Override
    public void complete(String runId, java.util.Map<String, Object> outputs, Instant finishedAt) {
        var update = new xin.v5ai.nb.workflow.domain.WorkflowRun();
        update.setRunId(runId);
        update.setStatus(WorkflowRunStatus.SUCCEEDED.name());
        update.setOutputsJson(WorkflowJson.toJson(outputs));
        update.setFinishedAt(finishedAt);
        runMapper.updateById(update);
    }

    @Override
    public void fail(String runId, String error, Instant finishedAt) {
        var update = new xin.v5ai.nb.workflow.domain.WorkflowRun();
        update.setRunId(runId);
        update.setStatus(WorkflowRunStatus.FAILED.name());
        update.setError(error);
        update.setFinishedAt(finishedAt);
        runMapper.updateById(update);
    }

    @Override
    public void saveNodeRun(WorkflowNodeRun nodeRun) {
        var entity = new xin.v5ai.nb.workflow.domain.WorkflowNodeRun();
        entity.setRunId(nodeRun.runId());
        entity.setNodeId(nodeRun.nodeId());
        entity.setNodeType(nodeRun.nodeType() == null ? null : nodeRun.nodeType().name());
        entity.setStatus(nodeRun.status() == null ? null : nodeRun.status().name());
        entity.setInputsJson(WorkflowJson.toJson(nodeRun.inputs()));
        entity.setOutputsJson(WorkflowJson.toJson(nodeRun.outputs()));
        entity.setError(nodeRun.error());
        entity.setStartedAt(nodeRun.startedAt());
        entity.setFinishedAt(nodeRun.finishedAt());
        entity.setCreatedAt(Instant.now());
        nodeRunMapper.insert(entity);
    }

    @Override
    public WorkflowRun findRun(String runId) {
        return toDomain(runMapper.selectById(runId));
    }

    @Override
    public List<WorkflowNodeRun> findNodeRuns(String runId) {
        return nodeRunMapper.selectList(new LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.WorkflowNodeRun>()
                        .eq(xin.v5ai.nb.workflow.domain.WorkflowNodeRun::getRunId, runId)
                        .orderByAsc(xin.v5ai.nb.workflow.domain.WorkflowNodeRun::getId))
                .stream()
                .map(this::toNodeRunDomain)
                .toList();
    }

    @Override
    public List<WorkflowRun> listRuns(String workflowKey) {
        return runMapper.selectList(new LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.WorkflowRun>()
                        .eq(xin.v5ai.nb.workflow.domain.WorkflowRun::getWorkflowKey, workflowKey)
                        .orderByDesc(xin.v5ai.nb.workflow.domain.WorkflowRun::getCreatedAt))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private WorkflowRun toDomain(xin.v5ai.nb.workflow.domain.WorkflowRun entity) {
        if (entity == null) {
            return null;
        }
        return new WorkflowRun(entity.getRunId(), entity.getWorkflowKey(), entity.getWorkflowVersion(),
                entity.getStatus() == null ? null : WorkflowRunStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseMap(entity.getInputsJson()),
                WorkflowJson.parseMap(entity.getOutputsJson()),
                entity.getError(), entity.getStartedAt(), entity.getFinishedAt(), entity.getCreatedAt());
    }

    private WorkflowNodeRun toNodeRunDomain(xin.v5ai.nb.workflow.domain.WorkflowNodeRun entity) {
        return new WorkflowNodeRun(entity.getId(), entity.getRunId(), entity.getNodeId(),
                entity.getNodeType() == null ? null : WorkflowNodeType.valueOf(entity.getNodeType()),
                entity.getStatus() == null ? null : WorkflowNodeRunStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseMap(entity.getInputsJson()),
                WorkflowJson.parseMap(entity.getOutputsJson()),
                entity.getError(), entity.getStartedAt(), entity.getFinishedAt());
    }

    private static xin.v5ai.nb.workflow.domain.WorkflowRun toEntity(WorkflowRun run) {
        var entity = new xin.v5ai.nb.workflow.domain.WorkflowRun();
        entity.setRunId(run.runId());
        entity.setWorkflowKey(run.workflowKey());
        entity.setWorkflowVersion(run.workflowVersion());
        entity.setStatus(run.status() == null ? null : run.status().name());
        entity.setInputsJson(WorkflowJson.toJson(run.inputs()));
        entity.setOutputsJson(WorkflowJson.toJson(run.outputs()));
        entity.setError(run.error());
        entity.setStartedAt(run.startedAt());
        entity.setFinishedAt(run.finishedAt());
        entity.setCreatedAt(run.createdAt() == null ? Instant.now() : run.createdAt());
        return entity;
    }
}
