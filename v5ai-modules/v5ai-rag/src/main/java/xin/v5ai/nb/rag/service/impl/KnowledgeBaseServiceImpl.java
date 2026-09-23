package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.fetcher.UrlContentFetcher;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.KnowledgeTask;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.domain.bo.KbConfigUpdateBo;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;
import xin.v5ai.nb.rag.mapper.AgentKnowledgeBindingMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeTaskMapper;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    /**
     * 文档来源类型: 上传
     */
    private static final String SOURCE_TYPE_UPLOAD = "UPLOAD";

    /**
     * 文档来源类型: 网络
     */
    private static final String SOURCE_TYPE_URL = "URL";

    /**
     * 源文件资源业务类型: DOCUMENT（写入 plm_resource）
     */
    private static final String RESOURCE_BIZ_TYPE_DOCUMENT = "DOCUMENT";

    /**
     * 文档索引状态: 0-待处理
     */
    private static final int DOC_STATUS_PENDING = 0;

    /**
     * 分类: 向量库
     */
    private static final int CATEGORY_VECTOR = 1;

    /**
     * 分类: 搜索引擎
     */
    private static final int CATEGORY_SEARCH = 2;

    /**
     * 文档分割符默认值
     */
    private static final String DEFAULT_DELIMITER = "\n\n";

    /**
     * 去重策略默认值: 2=BY_CONTENT
     */
    private static final int DEFAULT_DEDUP_STRATEGY = 2;

    /**
     * 冲突动作默认值: 0=REJECT
     */
    private static final int DEFAULT_DEDUP_ACTION = 0;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeTaskMapper taskMapper;
    private final AgentKnowledgeBindingMapper bindingMapper;
    private final StoreInstanceMapper storeInstanceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorStoreResolver vectorStoreResolver;
    private final UrlContentFetcher urlContentFetcher;
    private final xin.v5ai.nb.rag.service.IKnowledgeChunkService chunkService;
    private final xin.v5ai.nb.rag.core.VectorDimensionService vectorDimensionService;
    private final ResourceContentPort resourceContentPort;

    @Override
    public PageResult<KnowledgeBaseVo> queryPageList(KnowledgeBaseBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<KnowledgeBase> lqw = buildQueryWrapper(bo);
        Page<KnowledgeBase> page = pageQuery.build();
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(page, lqw);
        List<KnowledgeBaseVo> vos = list.stream().map(this::toVo).toList();
        enrichList(vos, list);
        return PageResult.build(vos, page.getTotal());
    }

    /**
     * 列表页批量回填文档数 / 切片数 / 被引用 AgentDTO：各一次按知识库 ID 的聚合查询，避免逐库 N+1。
     */
    private void enrichList(List<KnowledgeBaseVo> vos, List<KnowledgeBase> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(KnowledgeBase::getId).toList();
        Map<Long, Long> docCounts = countByKnowledgeBaseId(documentMapper, ids);
        Map<Long, Long> chunkCounts = countByKnowledgeBaseId(chunkMapper, ids);
        Map<Long, List<String>> references = new HashMap<>();
        for (AgentKnowledgeBinding binding : bindingMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBinding>()
                        .in(AgentKnowledgeBinding::getKnowledgeBaseId, ids))) {
            references.computeIfAbsent(binding.getKnowledgeBaseId(), k -> new ArrayList<>())
                    .add(binding.getAgentKey());
        }
        for (KnowledgeBaseVo vo : vos) {
            vo.setDocCount(docCounts.getOrDefault(vo.getId(), 0L));
            vo.setChunkCount(chunkCounts.getOrDefault(vo.getId(), 0L));
            vo.setReferencedAgentKeys(references.getOrDefault(vo.getId(), List.of()));
        }
    }

    /**
     * 按知识库 ID 列表统计行数（id → 数量；无匹配返回空 Map，缺省视为 0）。
     */
    private static <T> Map<Long, Long> countByKnowledgeBaseId(BaseMapper<T> mapper, List<Long> ids) {
        QueryWrapper<T> qw = new QueryWrapper<T>()
                .select("knowledge_base_id AS kb_id", "COUNT(*) AS cnt")
                .in("knowledge_base_id", ids)
                .groupBy("knowledge_base_id");
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : mapper.selectMaps(qw)) {
            if (row.get("kb_id") instanceof Number kbId && row.get("cnt") instanceof Number cnt) {
                counts.put(kbId.longValue(), cnt.longValue());
            }
        }
        return counts;
    }

    /**
     * 查询绑定某知识库的 AgentDTO 标识列表（删除前校验引用用）。
     */
    private List<String> findReferencingAgentKeys(Long knowledgeBaseId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                        .eq(AgentKnowledgeBinding::getKnowledgeBaseId, knowledgeBaseId))
                .stream()
                .map(AgentKnowledgeBinding::getAgentKey)
                .toList();
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
        validKnowledgeBaseBeforeSave(bo);
        var base = new KnowledgeBase();
        base.setName(name.trim());
        base.setDescription(bo.getDescription());
        base.setStatus(STATUS_ACTIVE);
        applyConfig(base, bo);
        knowledgeBaseMapper.insert(base);
        return toVo(base);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseVo updateKnowledgeBase(KnowledgeBaseBo bo) {
        KnowledgeBase existing = requireKnowledgeBase(bo.getId());
        validKnowledgeBaseBeforeSave(bo);
        var update = new KnowledgeBase();
        update.setId(existing.getId());
        update.setName(bo.getName() == null || bo.getName().isBlank() ? existing.getName() : bo.getName().trim());
        update.setDescription(bo.getDescription() == null ? existing.getDescription() : bo.getDescription());
        update.setStatus(existing.getStatus());
        applyConfig(update, bo);
        knowledgeBaseMapper.updateById(update);
        return toVo(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRagConfig(Long id, KbConfigUpdateBo bo) {
        KnowledgeBase existing = requireKnowledgeBase(id);
        if (bo == null || (bo.searchParams() == null && bo.modelParams() == null)) {
            throw new ServiceException("searchParams / modelParams 至少提供一项");
        }
        RagConfigDO config = RagConfigDO.fromJson(existing.getConfig());
        if (config == null) {
            config = new RagConfigDO();
        }
        // 整对象替换（null 键保留存量，与 updateKnowledgeBase 的「null 不覆盖」约定一致）
        if (bo.searchParams() != null) {
            config.setSearchParams(bo.searchParams());
        }
        if (bo.modelParams() != null) {
            config.setModelParams(bo.modelParams());
        }
        var update = new KnowledgeBase();
        update.setId(id);
        update.setConfig(RagConfigDO.toJson(config));
        knowledgeBaseMapper.updateById(update);
    }

    @Override
    public KnowledgeBaseVo getKnowledgeBase(Long id) {
        return toVo(requireKnowledgeBase(id));
    }

    @Override
    public KnowledgeBaseVo getKnowledgeBaseDetail(Long id) {
        KnowledgeBaseVo vo = toVo(requireKnowledgeBase(id));
        vo.setDocCount(documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id)));
        vo.setChunkCount(chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, id)));
        return vo;
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
    public void enableKnowledgeBase(Long id) {
        requireKnowledgeBase(id);
        var update = new KnowledgeBase();
        update.setId(id);
        update.setStatus(STATUS_ACTIVE);
        knowledgeBaseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase base = requireKnowledgeBase(id);
        List<String> referencingAgents = findReferencingAgentKeys(id);
        if (!referencingAgents.isEmpty()) {
            throw new ServiceException(
                    "该知识库正被 AgentDTO（" + String.join(", ", referencingAgents) + "）绑定，请先解除绑定后再删除");
        }
        // 1) 逐文档清理切片业务行与向量（含关键词后端），再删文档行
        List<KnowledgeDocument> documents = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKnowledgeBaseId, id));
        for (KnowledgeDocument document : documents) {
            chunkService.deleteByDocumentId(id, document.getId());
            if (base.getVectorStoreInstanceId() != null) {
                vectorStoreResolver.vectorStore(base.getVectorStoreInstanceId())
                        .deleteByDocumentId(id, document.getId());
                if (Boolean.TRUE.equals(base.getSearchEngineEnable())
                        && base.getSearchEngineInstanceId() != null
                        && !base.getSearchEngineInstanceId().equals(base.getVectorStoreInstanceId())) {
                    var keywordStore = vectorStoreResolver.keywordStore(base.getSearchEngineInstanceId());
                    if (keywordStore != null) {
                        keywordStore.deleteByDocumentId(id, document.getId());
                    }
                }
            }
            // 源文件存资源存储：知识库级联删除时同步清理关联资源（幂等、失败不阻断）
            deleteLinkedResourceQuietly(document.getResourceId());
        }
        // 2) 清理索引任务与绑定、文档行，最后删知识库本体
        taskMapper.delete(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getKnowledgeBaseId, id));
        bindingMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                .eq(AgentKnowledgeBinding::getKnowledgeBaseId, id));
        documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id));
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVo uploadDocument(Long knowledgeBaseId, String title, String originalFilename,
                                              DocumentFileType fileType, byte[] content, String contentType) {
        return createResourceBackedDocument(knowledgeBaseId, title,
                StrUtil.isBlank(originalFilename) ? title : originalFilename,
                fileType, SOURCE_TYPE_UPLOAD, contentType, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVo importUrl(Long knowledgeBaseId, String title, String url) {
        requireKnowledgeBase(knowledgeBaseId);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        var downloaded = urlContentFetcher.fetch(url);
        var bytes = downloaded.bytes() == null ? new byte[0] : downloaded.bytes();
        var fileType = DocumentFileType.fromUrl(url, downloaded.contentType());
        var resolvedTitle = title == null || title.isBlank() ? url : title;
        var contentType = downloaded.contentType() == null || downloaded.contentType().isBlank()
                ? fileType.defaultContentType() : downloaded.contentType();
        return createResourceBackedDocument(knowledgeBaseId, resolvedTitle,
                resolveUrlResourceName(resolvedTitle, fileType),
                fileType, SOURCE_TYPE_URL, contentType, bytes);
    }

    /**
     * 落库文档行（BYTEA 不再写入源文件）→ 提交 PENDING 任务 → 源文件写入资源存储（DOCUMENT）→ 回填 resource_id。
     * 资源写入失败抛异常，由调用方 {@code @Transactional} 回滚文档行与任务。
     */
    private KnowledgeDocumentVo createResourceBackedDocument(Long knowledgeBaseId, String title, String resourceName,
                                                             DocumentFileType fileType, String sourceType,
                                                             String contentType, byte[] content) {
        requireKnowledgeBase(knowledgeBaseId);
        if (title == null || title.isBlank() || content == null || content.length == 0) {
            throw new IllegalArgumentException("document title and content are required");
        }
        var document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setTitle(title);
        document.setFileType(fileType.name());
        document.setSourceType(sourceType);
        document.setStatus(DOC_STATUS_PENDING);
        document.setFileSize((long) content.length);
        document.setContentHash(sha256Hex(content));
        documentMapper.insert(document);
        submitIndexTask(document);
        Long resourceId = resourceContentPort.save(resourceName, content, contentType,
                RESOURCE_BIZ_TYPE_DOCUMENT, document.getId());
        var update = new KnowledgeDocument();
        update.setId(document.getId());
        update.setResourceId(resourceId);
        documentMapper.updateById(update);
        document.setResourceId(resourceId);
        return toDocumentVo(document);
    }

    /**
     * URL 文档源文件文件名：以标题为名、按判定类型补对应扩展名。
     */
    private static String resolveUrlResourceName(String title, DocumentFileType fileType) {
        String base = StrUtil.isBlank(title) ? "document" : title;
        String ext = fileType.extension();
        if (ext.isEmpty()) {
            return base;
        }
        return base.toLowerCase(java.util.Locale.ROOT).endsWith(ext) ? base : base + ext;
    }

    /**
     * 计算文件内容 SHA-256 十六进制摘要（用于去重）。
     */
    private static String sha256Hex(byte[] content) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
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
        // 业务行（内容/元数据）与向量解耦存储：删除文档时先清业务行，再按存储实例清向量
        chunkService.deleteByDocumentId(document.getKnowledgeBaseId(), documentId);
        var knowledgeBase = knowledgeBaseMapper.selectById(document.getKnowledgeBaseId());
        if (knowledgeBase != null && knowledgeBase.getVectorStoreInstanceId() != null) {
            vectorStoreResolver.vectorStore(knowledgeBase.getVectorStoreInstanceId())
                    .deleteByDocumentId(document.getKnowledgeBaseId(), documentId);
            if (Boolean.TRUE.equals(knowledgeBase.getSearchEngineEnable())
                    && knowledgeBase.getSearchEngineInstanceId() != null
                    && !knowledgeBase.getSearchEngineInstanceId().equals(knowledgeBase.getVectorStoreInstanceId())) {
                var keywordStore = vectorStoreResolver.keywordStore(knowledgeBase.getSearchEngineInstanceId());
                if (keywordStore != null) {
                    keywordStore.deleteByDocumentId(document.getKnowledgeBaseId(), documentId);
                }
            }
        }
        taskMapper.delete(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        deleteLinkedResourceQuietly(document.getResourceId());
    }

    /**
     * 删除文档关联的源文件资源（幂等、尽力而为：{@link ResourceContentPort#delete} 内部吞异常，不阻断文档删除）。
     */
    private void deleteLinkedResourceQuietly(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        resourceContentPort.delete(resourceId);
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
    @Transactional(rollbackFor = Exception.class)
    public boolean reparseDocument(Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("document does not exist: " + documentId);
        }
        Integer status = document.getStatus();
        // 0待处理/1解析中/2处理中：跳过（已在队列或进行中，避免竞态）；3完成/4失败：重置任务并回置状态后重跑
        if (status == null || status < 3) {
            return false;
        }
        for (KnowledgeTaskVo task : listTasksByDocument(documentId)) {
            retryTask(task.getId());
        }
        // 文档状态同步回置为「待处理」，使前端重新解析后立即反映排队状态（Worker 启动后再推进解析状态）
        var docUpdate = new KnowledgeDocument();
        docUpdate.setId(documentId);
        docUpdate.setStatus(DOC_STATUS_PENDING);
        documentMapper.updateById(docUpdate);
        return true;
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

    /**
     * 创建/编辑前校验：向量库/搜索引擎实例分类、维度、切片参数、去重参数。
     */
    private void validKnowledgeBaseBeforeSave(KnowledgeBaseBo bo) {
        if (bo.getDimensionOfVectorModel() == null || bo.getDimensionOfVectorModel() <= 0) {
            throw new ServiceException("向量维度必须大于 0");
        }
        requireStoreInstance(bo.getVectorStoreInstanceId(), CATEGORY_VECTOR, "向量库");
        // 权威校验：冻结维度须 ≤ min(模型维度, 向量库上限)；固定维度模型须与模型输出一致
        String dimensionError = vectorDimensionService.validateDimension(
                bo.getEmbeddingModelId(), bo.getVectorStoreInstanceId(), bo.getDimensionOfVectorModel());
        if (dimensionError != null) {
            throw new ServiceException(dimensionError);
        }
        if (Boolean.TRUE.equals(bo.getSearchEngineEnable())) {
            requireStoreInstance(bo.getSearchEngineInstanceId(), CATEGORY_SEARCH, "搜索引擎");
        }
        if (StrUtil.isNotBlank(bo.getDelimiter()) && bo.getDelimiter().length() > 32) {
            throw new ServiceException("文档分割符长度不能超过 32 字符");
        }
        if (bo.getConfig() != null && bo.getConfig().getChunkParams() != null) {
            validateChunkParams(bo.getConfig().getChunkParams());
        }
        if (bo.getConfig() != null && bo.getConfig().getParseParams() != null) {
            validateParseParams(bo.getConfig().getParseParams());
        }
        validateDedup(bo.getDedupStrategy(), bo.getDedupAction());
    }

    /**
     * 校验切片参数：模式取值、必填项与正则合法性。
     */
    private void validateChunkParams(RagConfigDO.ChunkParams chunkParams) {
        if (chunkParams.getMaxChunkLength() != null && chunkParams.getMaxChunkLength() <= 0) {
            throw new ServiceException("切片最大长度必须大于 0");
        }
        if (chunkParams.getChunkOverlap() != null && chunkParams.getChunkOverlap() < 0) {
            throw new ServiceException("片段重叠不能为负数");
        }
        if (ChunkMode.from(chunkParams.getSliceStrategy()) == null) {
            throw new ServiceException("切片策略不合法: length/delimiter/regex/smart");
        }
        String mode = chunkParams.getSliceStrategy().trim().toLowerCase();
        if ("delimiter".equals(mode) && StrUtil.isEmpty(chunkParams.getCustomDelimiter())) {
            throw new ServiceException("按分隔符切片必须配置分隔符");
        }
        if ("regex".equals(mode)) {
            if (StrUtil.isBlank(chunkParams.getChunkRegex())) {
                throw new ServiceException("正则切片必须配置一级切分正则");
            }
            try {
                Pattern.compile(chunkParams.getChunkRegex());
            } catch (PatternSyntaxException e) {
                throw new ServiceException("切片正则不合法: " + e.getMessage());
            }
        }
        if ("smart".equals(mode) && chunkParams.getChunkModelId() == null) {
            throw new ServiceException("智能切片必须配置对话模型");
        }
    }

    private void validateParseParams(RagConfigDO.ParseParams params) {
        String engine = StrUtil.blankToDefault(params.getEngine(), "default").trim().toLowerCase();
        if (!List.of("default", "docling", "mineru").contains(engine)) {
            throw new ServiceException("文档解析引擎不合法: default/docling/mineru");
        }
        if (params.getDocling() != null) {
            var docling = params.getDocling();
            if (docling.getImageExportMode() != null
                    && !List.of("placeholder", "embedded", "referenced").contains(docling.getImageExportMode())) {
                throw new ServiceException("Docling imageExportMode 不合法: placeholder/embedded/referenced");
            }
            if (docling.getMaxImageCount() != null && docling.getMaxImageCount() < 0) {
                throw new ServiceException("Docling maxImageCount 不能小于 0");
            }
            if (docling.getMaxImageBytes() != null && docling.getMaxImageBytes() < 0) {
                throw new ServiceException("Docling maxImageBytes 不能小于 0");
            }
        }
        if (params.getMineru() == null) return;
        var mineru = params.getMineru();
        if (mineru.getBackend() != null && !List.of("pipeline", "vlm-engine", "hybrid-engine", "vlm-http-client", "hybrid-http-client").contains(mineru.getBackend())) {
            throw new ServiceException("MinerU backend 不合法");
        }
        if (mineru.getEffort() != null && !List.of("medium", "high").contains(mineru.getEffort())) {
            throw new ServiceException("MinerU effort 不合法: medium/high");
        }
        if (mineru.getParseMethod() != null && !List.of("auto", "txt", "ocr").contains(mineru.getParseMethod())) {
            throw new ServiceException("MinerU parseMethod 不合法: auto/txt/ocr");
        }
        if (mineru.getStartPageId() != null && mineru.getStartPageId() < 0) {
            throw new ServiceException("MinerU startPageId 不能小于 0");
        }
        if (mineru.getEndPageId() != null && mineru.getEndPageId() < 0) {
            throw new ServiceException("MinerU endPageId 不能小于 0");
        }
        if (mineru.getStartPageId() != null && mineru.getEndPageId() != null
                && mineru.getStartPageId() > mineru.getEndPageId()) {
            throw new ServiceException("MinerU 页码范围不合法");
        }
        if (mineru.getLangList() != null) {
            var supported = List.of("ch", "ch_server", "korean", "ta", "te", "ka", "th", "el", "arabic", "east_slavic", "cyrillic", "devanagari");
            if (mineru.getLangList().stream().anyMatch(lang -> !supported.contains(lang))) {
                throw new ServiceException("MinerU langList 包含不支持的语言");
            }
        }
    }

    /**
     * 校验去重策略与冲突动作取值。
     */
    private void validateDedup(Integer strategy, Integer action) {
        if (strategy != null && (strategy < 0 || strategy > 3)) {
            throw new ServiceException("去重策略不合法: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT");
        }
        if (action != null && (action < 0 || action > 2)) {
            throw new ServiceException("冲突动作不合法: 0=REJECT 1=SKIP 2=OVERWRITE");
        }
    }

    /**
     * 校验存储实例存在、启用且分类匹配。
     */
    private void requireStoreInstance(Long instanceId, int expectedCategory, String label) {
        if (instanceId == null) {
            throw new ServiceException(label + "不能为空");
        }
        StoreInstance store = storeInstanceMapper.selectById(instanceId);
        if (store == null || store.getCategory() == null || expectedCategory != store.getCategory()) {
            throw new ServiceException(label + "实例不存在或分类不匹配");
        }
        if (store.getStatus() != null && store.getStatus() == 0) {
            throw new ServiceException(label + "实例已停用");
        }
    }

    /**
     * 把 BO 的配置字段写入实体（含默认值）。
     */
    private void applyConfig(KnowledgeBase base, KnowledgeBaseBo bo) {
        base.setIcon(bo.getIcon());
        base.setEmbeddingModelId(bo.getEmbeddingModelId());
        base.setVectorStoreInstanceId(bo.getVectorStoreInstanceId());
        base.setDimensionOfVectorModel(bo.getDimensionOfVectorModel());
        base.setRerankModelId(bo.getRerankModelId());
        base.setSearchEngineEnable(Boolean.TRUE.equals(bo.getSearchEngineEnable()));
        base.setSearchEngineInstanceId(bo.getSearchEngineInstanceId());
        base.setDelimiter(StrUtil.isBlank(bo.getDelimiter()) ? DEFAULT_DELIMITER : bo.getDelimiter().trim());
        base.setRagEnhancement(bo.getRagEnhancement());
        base.setConfig(RagConfigDO.toJson(bo.getConfig()));
        base.setDedupStrategy(bo.getDedupStrategy() == null ? DEFAULT_DEDUP_STRATEGY : bo.getDedupStrategy());
        base.setDedupAction(bo.getDedupAction() == null ? DEFAULT_DEDUP_ACTION : bo.getDedupAction());
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
        vo.setIcon(base.getIcon());
        vo.setEmbeddingModelId(base.getEmbeddingModelId());
        vo.setVectorStoreInstanceId(base.getVectorStoreInstanceId());
        vo.setDimensionOfVectorModel(base.getDimensionOfVectorModel());
        vo.setRerankModelId(base.getRerankModelId());
        vo.setSearchEngineEnable(base.getSearchEngineEnable());
        vo.setSearchEngineInstanceId(base.getSearchEngineInstanceId());
        vo.setDelimiter(base.getDelimiter());
        vo.setRagEnhancement(base.getRagEnhancement());
        vo.setConfig(RagConfigDO.fromJson(base.getConfig()));
        vo.setDedupStrategy(base.getDedupStrategy());
        vo.setDedupAction(base.getDedupAction());
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
        vo.setSourceType(document.getSourceType());
        vo.setStatus(document.getStatus());
        vo.setErrorMessage(document.getErrorMessage());
        vo.setStorageType(document.getStorageType());
        vo.setStoragePath(document.getStoragePath());
        vo.setFileSize(document.getFileSize());
        vo.setChunkCount(document.getChunkCount());
        vo.setParseTime(document.getParseTime());
        vo.setContentHash(document.getContentHash());
        vo.setResourceId(document.getResourceId());
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
