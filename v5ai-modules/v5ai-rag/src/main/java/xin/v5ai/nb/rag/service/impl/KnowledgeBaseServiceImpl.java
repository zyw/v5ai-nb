package xin.v5ai.nb.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.fetcher.UrlContentFetcher;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.KnowledgeTask;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;
import xin.v5ai.nb.rag.mapper.AgentKnowledgeBindingMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeTaskMapper;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 知识库管理服务实现类。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String TASK_TYPE_PARSE_AND_INDEX = "PARSE_AND_INDEX";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeTaskMapper taskMapper;
    private final AgentKnowledgeBindingMapper bindingMapper;
    private final VectorStore vectorStore;
    private final UrlContentFetcher urlContentFetcher;

    @Override
    public PageResult<KnowledgeBaseVo> queryPageList(KnowledgeBaseBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<KnowledgeBase> lqw = buildQueryWrapper(bo);
        Page<KnowledgeBase> page = pageQuery.build();
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(page, lqw);
        List<KnowledgeBaseVo> vos = list.stream().map(this::toVo).toList();
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    public List<OptionDTO> queryOptionList() {
        return knowledgeBaseMapper.selectList(QueryBuilder.lambda(KnowledgeBase.class)
                        .orderByAsc(KnowledgeBase::getId)
                        .build())
                .stream()
                .map(base -> new OptionDTO(base.getId(), base.getName() + " (#" + base.getId() + ")"))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseVo createKnowledgeBase(KnowledgeBaseBo bo) {
        var name = bo == null ? null : bo.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("knowledge base name is required");
        }
        var base = new KnowledgeBase();
        base.setName(name.trim());
        base.setDescription(bo.getDescription());
        base.setStatus(STATUS_ACTIVE);
        knowledgeBaseMapper.insert(base);
        return toVo(base);
    }

    @Override
    public KnowledgeBaseVo getKnowledgeBase(Long id) {
        return toVo(requireKnowledgeBase(id));
    }

    @Override
    public void disableKnowledgeBase(Long id) {
        requireKnowledgeBase(id);
        var update = new KnowledgeBase();
        update.setId(id);
        update.setStatus(STATUS_DISABLED);
        knowledgeBaseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVo uploadDocument(Long knowledgeBaseId, String title, DocumentFileType fileType,
                                              byte[] content, String source) {
        requireKnowledgeBase(knowledgeBaseId);
        if (title == null || title.isBlank() || content == null || content.length == 0) {
            throw new IllegalArgumentException("document title and content are required");
        }
        var document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setTitle(title);
        document.setFileType(fileType.name());
        document.setSource(source);
        document.setStatus(STATUS_PENDING);
        document.setContent(content);
        documentMapper.insert(document);
        submitIndexTask(document);
        return toDocumentVo(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVo importUrl(Long knowledgeBaseId, String title, String url) {
        requireKnowledgeBase(knowledgeBaseId);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        var content = urlContentFetcher.fetch(url);
        return uploadDocument(knowledgeBaseId,
                title == null || title.isBlank() ? url : title,
                DocumentFileType.URL, content.getBytes(StandardCharsets.UTF_8), url);
    }

    @Override
    public PageResult<KnowledgeDocumentVo> listDocuments(Long knowledgeBaseId, PageQuery pageQuery) {
        requireKnowledgeBase(knowledgeBaseId);
        // 列表不加载 BYTEA 原始内容列
        LambdaQueryWrapper<KnowledgeDocument> lqw = new LambdaQueryWrapper<KnowledgeDocument>()
                .select(KnowledgeDocument.class, field -> !"content".equals(field.getProperty()))
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocument::getId);
        Page<KnowledgeDocument> page = pageQuery.build();
        List<KnowledgeDocument> list = documentMapper.selectList(page, lqw);
        List<KnowledgeDocumentVo> vos = list.stream().map(this::toDocumentVo).toList();
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        var document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("document does not exist: " + documentId);
        }
        vectorStore.deleteByDocumentId(documentId);
        taskMapper.delete(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
    }

    @Override
    public void retryTask(Long taskId) {
        var task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task does not exist: " + taskId);
        }
        var update = new KnowledgeTask();
        update.setId(taskId);
        update.setStatus(STATUS_PENDING);
        taskMapper.update(update, new LambdaUpdateWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getId, taskId)
                .set(KnowledgeTask::getErrorMessage, null));
    }

    @Override
    public List<KnowledgeTaskVo> listTasks(Long knowledgeBaseId) {
        return taskMapper.selectList(new LambdaQueryWrapper<KnowledgeTask>()
                        .eq(KnowledgeTask::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(KnowledgeTask::getId))
                .stream()
                .map(this::toTaskVo)
                .toList();
    }

    @Override
    public List<KnowledgeTaskVo> listTasksByDocument(Long documentId) {
        return taskMapper.selectList(new LambdaQueryWrapper<KnowledgeTask>()
                        .eq(KnowledgeTask::getDocumentId, documentId))
                .stream()
                .map(this::toTaskVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindKnowledgeBases(String agentKey, List<Long> knowledgeBaseIds) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey is required");
        }
        if (knowledgeBaseIds == null) {
            throw new IllegalArgumentException("knowledgeBaseIds is required");
        }
        for (Long id : knowledgeBaseIds) {
            requireKnowledgeBase(id);
        }
        bindingMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                .eq(AgentKnowledgeBinding::getAgentKey, agentKey));
        for (Long id : knowledgeBaseIds) {
            var binding = new AgentKnowledgeBinding();
            binding.setAgentKey(agentKey);
            binding.setKnowledgeBaseId(id);
            bindingMapper.insert(binding);
        }
    }

    @Override
    public List<Long> getKnowledgeBindings(String agentKey) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                        .eq(AgentKnowledgeBinding::getAgentKey, agentKey))
                .stream()
                .map(AgentKnowledgeBinding::getKnowledgeBaseId)
                .toList();
    }

    /**
     * 构造知识库列表查询条件。
     */
    private LambdaQueryWrapper<KnowledgeBase> buildQueryWrapper(KnowledgeBaseBo bo) {
        return QueryBuilder.lambda(KnowledgeBase.class)
                .likeIfText(KnowledgeBase::getName, bo == null ? null : bo.getName())
                .eqIfText(KnowledgeBase::getStatus, bo == null ? null : bo.getStatus())
                .orderByAsc(KnowledgeBase::getId)
                .build();
    }

    private void submitIndexTask(KnowledgeDocument document) {
        var task = new KnowledgeTask();
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(document.getId());
        task.setTaskType(TASK_TYPE_PARSE_AND_INDEX);
        task.setStatus(STATUS_PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        taskMapper.insert(task);
    }

    private KnowledgeBase requireKnowledgeBase(Long knowledgeBaseId) {
        var base = knowledgeBaseId == null ? null : knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) {
            throw new IllegalArgumentException(
                    "knowledge base does not exist: " + knowledgeBaseId + legacyIdHint(knowledgeBaseId));
        }
        return base;
    }

    /**
     * IDs above Number.MAX_SAFE_INTEGER cannot be represented exactly in JavaScript;
     * they originate from the pre-fix Snowflake id generation and cannot be
     * round-tripped by the web UI. Surface a hint instead of a confusing lookup miss.
     */
    private static String legacyIdHint(Long id) {
        return id != null && id > 9_007_199_254_740_991L
                ? " (该 ID 超过 JavaScript 安全整数范围，属于修复前的雪花 ID，前端无法精确回传；请在页面重新创建知识库)"
                : "";
    }

    private KnowledgeBaseVo toVo(KnowledgeBase base) {
        var vo = new KnowledgeBaseVo();
        vo.setId(base.getId());
        vo.setName(base.getName());
        vo.setDescription(base.getDescription());
        vo.setStatus(base.getStatus());
        vo.setCreatedAt(base.getCreatedAt());
        vo.setUpdatedAt(base.getUpdatedAt());
        return vo;
    }

    private KnowledgeDocumentVo toDocumentVo(KnowledgeDocument document) {
        var vo = new KnowledgeDocumentVo();
        vo.setId(document.getId());
        vo.setKnowledgeBaseId(document.getKnowledgeBaseId());
        vo.setTitle(document.getTitle());
        vo.setFileType(document.getFileType());
        vo.setSource(document.getSource());
        vo.setStatus(document.getStatus());
        vo.setErrorMessage(document.getErrorMessage());
        vo.setCreatedAt(document.getCreatedAt());
        vo.setUpdatedAt(document.getUpdatedAt());
        return vo;
    }

    private KnowledgeTaskVo toTaskVo(KnowledgeTask task) {
        var vo = new KnowledgeTaskVo();
        vo.setId(task.getId());
        vo.setKnowledgeBaseId(task.getKnowledgeBaseId());
        vo.setDocumentId(task.getDocumentId());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setAttemptCount(task.getAttemptCount());
        vo.setMaxAttempts(task.getMaxAttempts());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }
}
