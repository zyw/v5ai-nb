package xin.v5ai.nb.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.workflow.core.*;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunSource;
import xin.v5ai.nb.workflow.domain.bo.WorkflowBo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowNodeRunDetailVo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowRunDetailVo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowVo;
import xin.v5ai.nb.workflow.mapper.WorkflowMapper;
import xin.v5ai.nb.workflow.mapper.WorkflowNodeRunMapper;
import xin.v5ai.nb.workflow.mapper.WorkflowRunMapper;
import xin.v5ai.nb.workflow.mapper.WorkflowVersionMapper;
import xin.v5ai.nb.workflow.domain.WorkflowVersion;
import xin.v5ai.nb.workflow.service.IWorkflowService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Workflow 管理服务实现类。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements IWorkflowService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final int MAX_BATCH_DELETE_SIZE = 100;

    private final WorkflowMapper workflowMapper;
    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private WorkflowVersionMapper versionMapper;

    @Autowired
    public void setVersionMapper(WorkflowVersionMapper versionMapper) { this.versionMapper = versionMapper; }

    @Override
    public PageResult<WorkflowVo> queryPageList(WorkflowBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.Workflow> lqw = buildQueryWrapper(bo);
        Page<xin.v5ai.nb.workflow.domain.Workflow> page = pageQuery.build();
        List<xin.v5ai.nb.workflow.domain.Workflow> list = workflowMapper.selectList(page, lqw);
        List<WorkflowVo> vos = list.stream().map(this::toVo).toList();
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    public List<WorkflowVo> queryList(WorkflowBo bo) {
        return workflowMapper.selectList(buildQueryWrapper(bo)).stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVo create(WorkflowBo bo) {
        if (bo.getWorkflowKey() == null || bo.getWorkflowKey().isBlank()) {
            throw new IllegalArgumentException("workflowKey is required");
        }
        if (bo.getName() == null || bo.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (workflowMapper.selectOne(new LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.Workflow>()
                .eq(xin.v5ai.nb.workflow.domain.Workflow::getWorkflowKey, bo.getWorkflowKey())) != null) {
            throw new IllegalArgumentException("workflow already exists: " + bo.getWorkflowKey());
        }
        var entity = new xin.v5ai.nb.workflow.domain.Workflow();
        entity.setWorkflowKey(bo.getWorkflowKey().trim());
        entity.setName(bo.getName().trim());
        entity.setDescription(bo.getDescription());
        entity.setStatus(STATUS_DRAFT);
        workflowMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVo update(String workflowKey, WorkflowBo bo) {
        var existing = requireWorkflow(workflowKey);
        long currentRevision = existing.getDraftRevision() == null ? 0L : existing.getDraftRevision();
        if (bo.getExpectedRevision() != null && !bo.getExpectedRevision().equals(currentRevision)) {
            throw new IllegalStateException("workflow draft revision conflict; reload before saving");
        }
        var update = new xin.v5ai.nb.workflow.domain.Workflow();
        update.setId(existing.getId());
        update.setWorkflowKey(existing.getWorkflowKey());
        update.setName(bo.getName() == null || bo.getName().isBlank() ? existing.getName() : bo.getName().trim());
        update.setDescription(bo.getDescription() == null ? existing.getDescription() : bo.getDescription());
        update.setDraftDefinitionJson(bo.getDefinition() == null
                ? existing.getDraftDefinitionJson()
                : WorkflowJson.toJson(bo.getDefinition()));
        update.setStatus(existing.getStatus());
        update.setPublishedDefinitionJson(existing.getPublishedDefinitionJson());
        update.setPublishedVersion(existing.getPublishedVersion());
        update.setPublishedAt(existing.getPublishedAt());
        update.setDraftRevision(bo.getDefinition() == null ? currentRevision : currentRevision + 1);
        workflowMapper.updateById(update);
        return toVo(update);
    }

    @Override
    public WorkflowVo get(String workflowKey) {
        return toVo(requireWorkflow(workflowKey));
    }

    @Override
    public void disable(String workflowKey) {
        var existing = requireWorkflow(workflowKey);
        var update = new xin.v5ai.nb.workflow.domain.Workflow();
        update.setId(existing.getId());
        update.setStatus(STATUS_DISABLED);
        workflowMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<String> workflowKeys) {
        if (workflowKeys == null || workflowKeys.isEmpty()) {
            throw new IllegalArgumentException("workflowKeys must not be empty");
        }
        if (workflowKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("workflowKeys must not contain blank values");
        }
        Set<String> distinctKeys = workflowKeys.stream()
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctKeys.size() > MAX_BATCH_DELETE_SIZE) {
            throw new IllegalArgumentException("at most 100 workflows can be deleted at once");
        }

        List<String> keys = new ArrayList<>(distinctKeys);
        List<xin.v5ai.nb.workflow.domain.Workflow> found = workflowMapper.selectBatchForUpdate(keys);
        if (found.size() != keys.size()) {
            throw new IllegalArgumentException("one or more workflows do not exist");
        }
        if (found.stream().anyMatch(workflow -> !STATUS_DRAFT.equals(workflow.getStatus())
                && !STATUS_DISABLED.equals(workflow.getStatus()))) {
            throw new IllegalArgumentException("only draft or disabled workflows can be deleted");
        }

        List<xin.v5ai.nb.workflow.domain.WorkflowRun> runs = runMapper.selectList(
                new QueryWrapper<xin.v5ai.nb.workflow.domain.WorkflowRun>().in("workflow_key", keys));
        if (runs.stream().anyMatch(run -> "RUNNING".equals(run.getStatus()))) {
            throw new IllegalArgumentException("workflows with running workflow runs cannot be deleted");
        }
        List<String> runIds = runs.stream()
                .map(xin.v5ai.nb.workflow.domain.WorkflowRun::getRunId)
                .toList();
        if (!runIds.isEmpty()) {
            nodeRunMapper.delete(new QueryWrapper<xin.v5ai.nb.workflow.domain.WorkflowNodeRun>()
                    .in("run_id", runIds));
        }
        runMapper.delete(new QueryWrapper<xin.v5ai.nb.workflow.domain.WorkflowRun>().in("workflow_key", keys));
        if (versionMapper != null) {
            versionMapper.delete(new QueryWrapper<WorkflowVersion>().in("workflow_key", keys));
        }
        workflowMapper.delete(new QueryWrapper<xin.v5ai.nb.workflow.domain.Workflow>()
                .in("workflow_key", keys));
    }

    @Override
    public void enable(String workflowKey) {
        var existing = requireWorkflow(workflowKey);
        if (!STATUS_DISABLED.equals(existing.getStatus())) {
            return;
        }
        var update = new xin.v5ai.nb.workflow.domain.Workflow();
        update.setId(existing.getId());
        update.setStatus(existing.getPublishedVersion() == null ? STATUS_DRAFT : STATUS_PUBLISHED);
        workflowMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVo publish(String workflowKey) {
        var existing = requireActive(workflowKey);
        if (existing.getDraftDefinitionJson() == null || existing.getDraftDefinitionJson().isBlank()) {
            throw new IllegalArgumentException("workflow has no draft definition to publish: " + workflowKey);
        }
        WorkflowDefinition draft = WorkflowJson.parseDefinition(existing.getDraftDefinitionJson());
        WorkflowDefinitionValidator.validate(draft);
        long version = existing.getPublishedVersion() == null ? 1 : existing.getPublishedVersion() + 1;
        if (versionMapper != null) {
            var snapshot = new WorkflowVersion();
            snapshot.setWorkflowKey(workflowKey);
            snapshot.setVersion(version);
            snapshot.setDefinition(existing.getDraftDefinitionJson());
            snapshot.setSchemaVersion(draft.schemaVersion());
            snapshot.setPublishedAt(OffsetDateTime.now());
            versionMapper.insert(snapshot);
        }
        var update = new xin.v5ai.nb.workflow.domain.Workflow();
        update.setId(existing.getId());
        update.setStatus(STATUS_PUBLISHED);
        update.setPublishedDefinitionJson(existing.getDraftDefinitionJson());
        update.setPublishedVersion(version);
        update.setPublishedAt(OffsetDateTime.now());
        workflowMapper.updateById(update);
        existing.setStatus(STATUS_PUBLISHED);
        existing.setPublishedDefinitionJson(existing.getDraftDefinitionJson());
        existing.setPublishedVersion(version);
        existing.setPublishedAt(update.getPublishedAt());
        return toVo(existing);
    }

    @Override
    public List<WorkflowRun> listRuns(String workflowKey) {
        requireWorkflow(workflowKey);
        return runMapper.selectList(QueryBuilder.lambda(xin.v5ai.nb.workflow.domain.WorkflowRun.class)
                        .eq(xin.v5ai.nb.workflow.domain.WorkflowRun::getWorkflowKey, workflowKey)
                        .orderByDesc(xin.v5ai.nb.workflow.domain.WorkflowRun::getCreatedAt)
                        .build())
                .stream()
                .map(this::toRunDomain)
                .toList();
    }

    @Override
    public List<WorkflowVersion> listVersions(String workflowKey) {
        requireWorkflow(workflowKey);
        if (versionMapper == null) return List.of();
        return versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowKey, workflowKey)
                .orderByDesc(WorkflowVersion::getVersion));
    }

    @Override
    public WorkflowVersion getVersion(String workflowKey, long version) {
        if (versionMapper == null) throw new IllegalStateException("workflow version storage is unavailable");
        WorkflowVersion found = versionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowKey, workflowKey).eq(WorkflowVersion::getVersion, version));
        if (found == null) throw new IllegalArgumentException("workflow version does not exist");
        return found;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVo restoreVersion(String workflowKey, long version, Long expectedRevision) {
        var existing = requireActive(workflowKey);
        long revision = existing.getDraftRevision() == null ? 0L : existing.getDraftRevision();
        if (expectedRevision != null && !expectedRevision.equals(revision)) {
            throw new IllegalStateException("workflow draft revision conflict; reload before restoring");
        }
        WorkflowVersion snapshot = getVersion(workflowKey, version);
        WorkflowDefinition restored = WorkflowJson.parseDefinition(snapshot.getDefinition());
        var update = new xin.v5ai.nb.workflow.domain.Workflow();
        update.setId(existing.getId());
        update.setDraftDefinitionJson(WorkflowJson.toJson(restored));
        update.setDraftRevision(revision + 1);
        workflowMapper.updateById(update);
        existing.setDraftDefinitionJson(update.getDraftDefinitionJson());
        existing.setDraftRevision(revision + 1);
        return toVo(existing);
    }

    @Override
    public WorkflowRun getRun(String runId) {
        var run = toRunDomain(runMapper.selectById(runId));
        if (run == null) {
            throw new IllegalArgumentException("workflow run does not exist: " + runId);
        }
        return run;
    }

    @Override
    public WorkflowRunDetailVo getRunDetail(String runId) {
        WorkflowRun run = getRun(runId);
        WorkflowDefinition definition = resolveRunDefinition(run);
        Map<String, String> nodeNames = definition == null ? Map.of() : definition.nodes().stream()
                .filter(node -> node.id() != null && node.name() != null && !node.name().isBlank())
                .collect(Collectors.toMap(WorkflowNode::id, WorkflowNode::name, (first, ignored) -> first));
        List<WorkflowNodeRunDetailVo> details = getNodeRuns(runId).stream()
                .map(nodeRun -> new WorkflowNodeRunDetailVo(nodeRun.id(), nodeRun.runId(), nodeRun.nodeId(),
                        nodeNames.get(nodeRun.nodeId()), nodeRun.nodeType(), nodeRun.status(), nodeRun.inputs(),
                        nodeRun.outputs(), nodeRun.error(), nodeRun.startedAt(), nodeRun.finishedAt()))
                .toList();
        return new WorkflowRunDetailVo(run, details);
    }

    private WorkflowDefinition resolveRunDefinition(WorkflowRun run) {
        if (run.source() == WorkflowRunSource.DRAFT_TEST) {
            return WorkflowJson.parseDefinition(run.definitionSnapshot());
        }
        if (run.workflowVersion() != null && versionMapper != null) {
            WorkflowVersion version = versionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                    .eq(WorkflowVersion::getWorkflowKey, run.workflowKey())
                    .eq(WorkflowVersion::getVersion, run.workflowVersion()));
            if (version != null) {
                return WorkflowJson.parseDefinition(version.getDefinition());
            }
        }
        // Legacy runs may lack a version snapshot; use their own snapshot when available.
        return WorkflowJson.parseDefinition(run.definitionSnapshot());
    }

    @Override
    public List<WorkflowNodeRun> getNodeRuns(String runId) {
        return nodeRunMapper.selectList(QueryBuilder.lambda(xin.v5ai.nb.workflow.domain.WorkflowNodeRun.class)
                        .eq(xin.v5ai.nb.workflow.domain.WorkflowNodeRun::getRunId, runId)
                        .orderByAsc(xin.v5ai.nb.workflow.domain.WorkflowNodeRun::getId)
                        .build())
                .stream()
                .map(this::toNodeRunDomain)
                .toList();
    }

    private LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.Workflow> buildQueryWrapper(WorkflowBo bo) {
        return QueryBuilder.lambda(xin.v5ai.nb.workflow.domain.Workflow.class)
                .likeIfText(xin.v5ai.nb.workflow.domain.Workflow::getWorkflowKey,
                        bo == null ? null : bo.getWorkflowKey())
                .likeIfText(xin.v5ai.nb.workflow.domain.Workflow::getName, bo == null ? null : bo.getName())
                .eqIfText(xin.v5ai.nb.workflow.domain.Workflow::getStatus, bo == null ? null : bo.getStatus())
                .orderByAsc(xin.v5ai.nb.workflow.domain.Workflow::getId)
                .build();
    }

    private xin.v5ai.nb.workflow.domain.Workflow requireWorkflow(String workflowKey) {
        var workflow = workflowMapper.selectOne(new LambdaQueryWrapper<xin.v5ai.nb.workflow.domain.Workflow>()
                .eq(xin.v5ai.nb.workflow.domain.Workflow::getWorkflowKey, workflowKey));
        if (workflow == null) {
            throw new IllegalArgumentException("workflow does not exist: " + workflowKey);
        }
        return workflow;
    }

    private xin.v5ai.nb.workflow.domain.Workflow requireActive(String workflowKey) {
        var workflow = requireWorkflow(workflowKey);
        if (STATUS_DISABLED.equals(workflow.getStatus())) {
            throw new IllegalArgumentException("workflow is disabled: " + workflowKey);
        }
        return workflow;
    }

    private WorkflowVo toVo(xin.v5ai.nb.workflow.domain.Workflow entity) {
        var vo = new WorkflowVo();
        vo.setId(entity.getId());
        vo.setWorkflowKey(entity.getWorkflowKey());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setDraftDefinition(WorkflowJson.parseDefinition(entity.getDraftDefinitionJson()));
        vo.setPublishedDefinition(WorkflowJson.parseDefinition(entity.getPublishedDefinitionJson()));
        vo.setPublishedVersion(entity.getPublishedVersion());
        vo.setPublishedAt(entity.getPublishedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setDraftRevision(entity.getDraftRevision() == null ? 0L : entity.getDraftRevision());
        return vo;
    }

    private WorkflowRun toRunDomain(xin.v5ai.nb.workflow.domain.WorkflowRun entity) {
        if (entity == null) {
            return null;
        }
        return new WorkflowRun(entity.getRunId(), entity.getWorkflowKey(), entity.getWorkflowVersion(),
                entity.getStatus() == null ? null : WorkflowRunStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseMap(entity.getInputsJson()),
                WorkflowJson.parseMap(entity.getOutputsJson()),
                entity.getError(), entity.getStartedAt(), entity.getFinishedAt(), entity.getCreatedAt(),
                entity.getSource() == null ? xin.v5ai.nb.workflow.core.enums.WorkflowRunSource.PUBLISHED
                        : xin.v5ai.nb.workflow.core.enums.WorkflowRunSource.valueOf(entity.getSource()),
                entity.getDraftRevision(), entity.getDefinitionSnapshot());
    }

    private WorkflowNodeRun toNodeRunDomain(xin.v5ai.nb.workflow.domain.WorkflowNodeRun entity) {
        return new WorkflowNodeRun(entity.getId(), entity.getRunId(), entity.getNodeId(),
                entity.getNodeType() == null ? null : WorkflowNodeType.valueOf(entity.getNodeType()),
                entity.getStatus() == null ? null : WorkflowNodeRunStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseMap(entity.getInputsJson()),
                WorkflowJson.parseMap(entity.getOutputsJson()),
                entity.getError(), entity.getStartedAt(), entity.getFinishedAt());
    }
}
