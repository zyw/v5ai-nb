package xin.v5ai.nb.rag.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.fetcher.UrlContent;
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
import xin.v5ai.nb.rag.mapper.AgentKnowledgeBindingMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeTaskMapper;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeBaseServiceImpl} 的 Mockito 单测：mock Mapper + 假 VectorStoreResolver/UrlContentFetcher。
 *
 * @author ZYW
 * @since 2026-08-22
 */
class KnowledgeBaseServiceImplTest {

    private final List<KnowledgeBase> knowledgeBases = new ArrayList<>();
    private final List<KnowledgeDocument> documents = new ArrayList<>();
    private final List<KnowledgeTask> tasks = new ArrayList<>();
    private final List<AgentKnowledgeBinding> bindings = new ArrayList<>();
    private final List<StoreInstance> stores = new ArrayList<>();

    private KnowledgeBaseServiceImpl service;
    private ResourceContentPort resourceContentPort;

    @BeforeEach
    void setUp() {
        // 纯单测环境没有 MyBatis 启动流程，这里手动初始化实体 TableInfo，
        // 使服务内 LambdaUpdateWrapper/LambdaQueryWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeTask.class);
        TableInfoHelper.initTableInfo(assistant, AgentKnowledgeBinding.class);
        TableInfoHelper.initTableInfo(assistant, StoreInstance.class);

        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeTaskMapper taskMapper = mock(KnowledgeTaskMapper.class);
        AgentKnowledgeBindingMapper bindingMapper = mock(AgentKnowledgeBindingMapper.class);
        StoreInstanceMapper storeInstanceMapper = mock(StoreInstanceMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        VectorStoreResolver vectorStoreResolver = mock(VectorStoreResolver.class);
        UrlContentFetcher urlFetcher = mock(UrlContentFetcher.class);
        xin.v5ai.nb.rag.service.IKnowledgeChunkService chunkService =
                mock(xin.v5ai.nb.rag.service.IKnowledgeChunkService.class);
        xin.v5ai.nb.rag.core.VectorDimensionService vectorDimensionService =
                mock(xin.v5ai.nb.rag.core.VectorDimensionService.class);
        ResourceContentPort resourceContentPort = mock(ResourceContentPort.class);
        this.resourceContentPort = resourceContentPort;
        when(resourceContentPort.save(any(), any(byte[].class), any(), any(), any())).thenReturn(123L);

        when(knowledgeBaseMapper.selectById(any())).thenAnswer(inv -> findById(knowledgeBases, inv.getArgument(0)));
        when(knowledgeBaseMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(knowledgeBases));
        doAnswer(inv -> {
            var base = inv.getArgument(0, KnowledgeBase.class);
            base.setId((long) (knowledgeBases.size() + 1));
            knowledgeBases.add(base);
            return 1;
        }).when(knowledgeBaseMapper).insert(any(KnowledgeBase.class));
        doAnswer(inv -> {
            KnowledgeBase update = inv.getArgument(0, KnowledgeBase.class);
            knowledgeBases.stream().filter(b -> b.getId().equals(update.getId())).forEach(b -> {
                if (update.getStatus() != null) {
                    b.setStatus(update.getStatus());
                }
                if (update.getName() != null) {
                    b.setName(update.getName());
                }
                if (update.getConfig() != null) {
                    b.setConfig(update.getConfig());
                }
                if (update.getDelimiter() != null) {
                    b.setDelimiter(update.getDelimiter());
                }
                if (update.getDimensionOfVectorModel() != null) {
                    b.setDimensionOfVectorModel(update.getDimensionOfVectorModel());
                }
            });
            return 1;
        }).when(knowledgeBaseMapper).updateById(any(KnowledgeBase.class));

        when(storeInstanceMapper.selectById(any())).thenAnswer(inv -> findById(stores, inv.getArgument(0)));

        doAnswer(inv -> {
            var document = inv.getArgument(0, KnowledgeDocument.class);
            document.setId((long) (documents.size() + 1) * 10L);
            documents.add(document);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));
        when(documentMapper.selectById(any())).thenAnswer(inv -> findById(documents, inv.getArgument(0)));
        doAnswer(inv -> {
            KnowledgeDocument update = inv.getArgument(0, KnowledgeDocument.class);
            documents.stream().filter(d -> d.getId().equals(update.getId())).forEach(d -> {
                if (update.getResourceId() != null) {
                    d.setResourceId(update.getResourceId());
                }
                if (update.getStatus() != null) {
                    d.setStatus(update.getStatus());
                }
            });
            return 1;
        }).when(documentMapper).updateById(any(KnowledgeDocument.class));

        doAnswer(inv -> {
            var task = inv.getArgument(0, KnowledgeTask.class);
            task.setId((long) (tasks.size() + 1));
            tasks.add(task);
            return 1;
        }).when(taskMapper).insert(any(KnowledgeTask.class));
        when(taskMapper.selectById(any())).thenAnswer(inv -> findById(tasks, inv.getArgument(0)));
        when(taskMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(tasks));
        doAnswer(inv -> {
            KnowledgeTask update = inv.getArgument(0, KnowledgeTask.class);
            tasks.stream().filter(t -> t.getId().equals(update.getId())).forEach(t -> {
                if (update.getStatus() != null) {
                    t.setStatus(update.getStatus());
                }
            });
            return 1;
        }).when(taskMapper).update(any(KnowledgeTask.class), any(Wrapper.class));

        doAnswer(inv -> {
            var binding = inv.getArgument(0, AgentKnowledgeBinding.class);
            bindings.add(binding);
            return 1;
        }).when(bindingMapper).insert(any(AgentKnowledgeBinding.class));
        doAnswer(inv -> {
            bindings.removeIf(b -> b.getAgentKey().equals(inv.getArgument(0, String.class)));
            return 1;
        }).when(bindingMapper).delete(any(Wrapper.class));
        when(bindingMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(bindings));

        when(urlFetcher.fetch("https://example.com/doc")).thenReturn(new UrlContent(
                "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html"));

        service = new KnowledgeBaseServiceImpl(knowledgeBaseMapper, documentMapper, taskMapper,
                bindingMapper, storeInstanceMapper, chunkMapper, vectorStoreResolver, urlFetcher,
                chunkService, vectorDimensionService, resourceContentPort);
    }

    @Test
    void createKnowledgeBaseAssignsIdAndActiveStatus() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);
        bo.setDescription("product docs");

        var created = service.createKnowledgeBase(bo);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("Docs");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getEmbeddingModelId()).isEqualTo(11L);
        assertThat(created.getDimensionOfVectorModel()).isEqualTo(1536);
        assertThat(knowledgeBases).hasSize(1);
    }

    @Test
    void createKnowledgeBaseAppliesDefaults() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);

        var created = service.createKnowledgeBase(bo);

        assertThat(created.getDelimiter()).isEqualTo("\n\n");
        assertThat(created.getDedupStrategy()).isEqualTo(2);
        assertThat(created.getDedupAction()).isEqualTo(0);
        assertThat(created.getSearchEngineEnable()).isFalse();
    }

    @Test
    void createKnowledgeBaseRoundTripsConfigJson() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);
        var chunkParams = RagConfigDO.ChunkParams.builder()
                .sliceStrategy("delimiter").maxChunkLength(2000).chunkOverlap(100)
                .customDelimiter("\n\n").mergeShortSegments(true).build();
        bo.setConfig(RagConfigDO.builder()
                .chunkParams(chunkParams)
                .parseParams(RagConfigDO.ParseParams.builder().engine("docling").build())
                .build());

        var created = service.createKnowledgeBase(bo);

        assertThat(created.getConfig()).isNotNull();
        assertThat(created.getConfig().getChunkParams().getSliceStrategy()).isEqualTo("delimiter");
        assertThat(created.getConfig().getChunkParams().getMaxChunkLength()).isEqualTo(2000);
        assertThat(created.getConfig().getParseParams().getEngine()).isEqualTo("docling");
    }

    @Test
    void createKnowledgeBaseRejectsBlankName() {
        var bo = new KnowledgeBaseBo();
        bo.setName("  ");

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base name is required");
    }

    @Test
    void createKnowledgeBaseRejectsMissingVectorStore() {
        var bo = validBo("Docs", 11L, 99L, 1536);

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("向量库实例不存在");
    }

    @Test
    void createKnowledgeBaseRejectsWrongStoreCategory() {
        seedStore(1L, "es", 2, 3);
        var bo = validBo("Docs", 11L, 1L, 1536);

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("分类不匹配");
    }

    @Test
    void createKnowledgeBaseRejectsInvalidDimension() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 0);

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("向量维度必须大于 0");
    }

    @Test
    void createKnowledgeBaseWithSearchEngineRequiresEngineInstance() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);
        bo.setSearchEngineEnable(true);

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("搜索引擎不能为空");
    }

    @Test
    void createKnowledgeBaseRejectsBadChunkMode() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);
        bo.setConfig(RagConfigDO.builder()
                .chunkParams(RagConfigDO.ChunkParams.builder().sliceStrategy("bogus").build())
                .build());

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("切片策略不合法");
    }

    @Test
    void createKnowledgeBaseRejectsSmartWithoutModel() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);
        bo.setConfig(RagConfigDO.builder()
                .chunkParams(RagConfigDO.ChunkParams.builder().sliceStrategy("smart").build())
                .build());

        assertThatThrownBy(() -> service.createKnowledgeBase(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("智能切片必须配置对话模型");
    }

    @Test
    void createKnowledgeBaseAcceptsSmartWithModel() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("Docs", 11L, 1L, 1536);
        bo.setConfig(RagConfigDO.builder()
                .chunkParams(RagConfigDO.ChunkParams.builder().sliceStrategy("smart").chunkModelId(42L).build())
                .build());

        var created = service.createKnowledgeBase(bo);

        assertThat(created.getConfig().getChunkParams().getChunkModelId()).isEqualTo(42L);
    }

    @Test
    void updateKnowledgeBaseOverwritesConfigFields() {
        seedStore(1L, "pg", 1, 1);
        seedKnowledgeBase(1L, "docs", "ACTIVE");
        var bo = validBo("renamed", 12L, 1L, 768);
        bo.setId(1L);
        bo.setConfig(RagConfigDO.builder()
                .chunkParams(RagConfigDO.ChunkParams.builder().sliceStrategy("length").maxChunkLength(600).build())
                .build());

        var updated = service.updateKnowledgeBase(bo);

        assertThat(updated.getName()).isEqualTo("renamed");
        assertThat(updated.getEmbeddingModelId()).isEqualTo(12L);
        assertThat(updated.getDimensionOfVectorModel()).isEqualTo(768);
        assertThat(updated.getConfig().getChunkParams().getMaxChunkLength()).isEqualTo(600);
        assertThat(updated.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateKnowledgeBaseRejectsUnknown() {
        seedStore(1L, "pg", 1, 1);
        var bo = validBo("x", 11L, 1L, 1536);
        bo.setId(99L);

        assertThatThrownBy(() -> service.updateKnowledgeBase(bo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void updateRagConfigReplacesSearchAndModelKeepingChunkAndParse() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");
        knowledgeBases.get(0).setConfig(RagConfigDO.toJson(RagConfigDO.builder()
                .chunkParams(RagConfigDO.ChunkParams.builder().sliceStrategy("length").maxChunkLength(600).build())
                .parseParams(RagConfigDO.ParseParams.builder().engine("default").build())
                .searchParams(RagConfigDO.SearchParams.builder().resultCount(20).rrfK(60).build())
                .modelParams(RagConfigDO.ModelParams.builder().modelId(1L).nearbySliceCount(5).build())
                .build()));

        service.updateRagConfig(1L, new KbConfigUpdateBo(
                RagConfigDO.SearchParams.builder().resultCount(50).denseWeight(0.6).build(),
                RagConfigDO.ModelParams.builder().modelId(7L).nearbySliceCount(3).build()));

        var config = RagConfigDO.fromJson(knowledgeBases.get(0).getConfig());
        // chunkParams / parseParams 原样保留
        assertThat(config.getChunkParams().getSliceStrategy()).isEqualTo("length");
        assertThat(config.getChunkParams().getMaxChunkLength()).isEqualTo(600);
        assertThat(config.getParseParams().getEngine()).isEqualTo("default");
        // searchParams / modelParams 整对象替换（未提供的键被抹掉）
        assertThat(config.getSearchParams().getResultCount()).isEqualTo(50);
        assertThat(config.getSearchParams().getDenseWeight()).isEqualTo(0.6);
        assertThat(config.getSearchParams().getRrfK()).isNull();
        assertThat(config.getModelParams().getModelId()).isEqualTo(7L);
        assertThat(config.getModelParams().getNearbySliceCount()).isEqualTo(3);
        // 基本信息不受影响
        assertThat(knowledgeBases.get(0).getName()).isEqualTo("docs");
        assertThat(knowledgeBases.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateRagConfigNullBlockKeepsExisting() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");
        knowledgeBases.get(0).setConfig(RagConfigDO.toJson(RagConfigDO.builder()
                .searchParams(RagConfigDO.SearchParams.builder().resultCount(30).build())
                .modelParams(RagConfigDO.ModelParams.builder().modelId(1L).build())
                .build()));

        service.updateRagConfig(1L, new KbConfigUpdateBo(null,
                RagConfigDO.ModelParams.builder().modelId(9L).build()));

        var config = RagConfigDO.fromJson(knowledgeBases.get(0).getConfig());
        assertThat(config.getSearchParams().getResultCount()).isEqualTo(30);
        assertThat(config.getModelParams().getModelId()).isEqualTo(9L);
    }

    @Test
    void updateRagConfigRejectsEmptyBody() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");

        assertThatThrownBy(() -> service.updateRagConfig(1L, new KbConfigUpdateBo(null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少提供一项");
    }

    @Test
    void updateRagConfigRejectsUnknownKnowledgeBase() {
        assertThatThrownBy(() -> service.updateRagConfig(99L, new KbConfigUpdateBo(
                RagConfigDO.SearchParams.builder().resultCount(10).build(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void disableKnowledgeBaseUpdatesStatus() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");

        service.disableKnowledgeBase(1L);

        assertThat(knowledgeBases.get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void disableKnowledgeBaseRejectsUnknown() {
        assertThatThrownBy(() -> service.disableKnowledgeBase(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void optionsReturnLabelWithIdSuffix() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");
        seedKnowledgeBase(2L, "manual", "ACTIVE");

        var options = service.queryOptionList();

        assertThat(options).extracting(option -> option.value()).containsExactly(1L, 2L);
        assertThat(options).extracting(option -> option.label()).containsExactly("docs (#1)", "manual (#2)");
    }

    @Test
    void uploadDocumentStoresRowAndSubmitsPendingTask() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");
        var bytes = "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        var document = service.uploadDocument(1L, "guide.md", "guide.md", DocumentFileType.MARKDOWN,
                bytes, "text/markdown");

        assertThat(document.getId()).isEqualTo(10L);
        assertThat(document.getStatus()).isEqualTo(0);
        assertThat(document.getSourceType()).isEqualTo("UPLOAD");
        assertThat(document.getFileType()).isEqualTo("MARKDOWN");
        assertThat(document.getFileSize()).isEqualTo(bytes.length);
        assertThat(document.getContentHash()).isEqualTo(
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        // BYTEA 不再写入源文件；源文件写入资源存储并回填 resource_id
        assertThat(documents.get(0).getContent()).isNull();
        assertThat(documents.get(0).getResourceId()).isEqualTo(123L);
        verify(resourceContentPort).save("guide.md", bytes, "text/markdown", "DOCUMENT", 10L);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(tasks.get(0).getTaskType()).isEqualTo("PARSE_AND_INDEX");
        assertThat(tasks.get(0).getMaxAttempts()).isEqualTo(3);
        assertThat(tasks.get(0).getAttemptCount()).isZero();
    }

    @Test
    void uploadDocumentRejectsUnknownKnowledgeBase() {
        assertThatThrownBy(() -> service.uploadDocument(99L, "a.txt", "a.txt", DocumentFileType.TXT,
                new byte[]{1}, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void uploadDocumentWithLegacySnowflakeIdIncludesMigrationHint() {
        assertThatThrownBy(() -> service.uploadDocument(2088154694704398300L, "a.txt", "a.txt", DocumentFileType.TXT,
                new byte[]{1}, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist")
                .hasMessageContaining("JavaScript 安全整数");
    }

    @Test
    void importUrlFetchesContentAndStoresUrlDocument() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");

        var document = service.importUrl(1L, null, "https://example.com/doc");

        assertThat(document.getTitle()).isEqualTo("https://example.com/doc");
        assertThat(document.getFileType()).isEqualTo("HTML");
        assertThat(document.getSourceType()).isEqualTo("URL");
        // BYTEA 不写；内容以 .html 资源写入资源存储
        assertThat(documents.get(0).getContent()).isNull();
        assertThat(documents.get(0).getResourceId()).isEqualTo(123L);
        verify(resourceContentPort).save("https://example.com/doc.html",
                "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html", "DOCUMENT", 10L);
    }

    @Test
    void retryTaskResetsStatusToPendingAndClearsError() {
        var task = new KnowledgeTask();
        task.setId(7L);
        task.setKnowledgeBaseId(1L);
        task.setDocumentId(10L);
        task.setTaskType("PARSE_AND_INDEX");
        task.setStatus("FAILED");
        task.setAttemptCount(3);
        task.setMaxAttempts(3);
        task.setErrorMessage("boom");
        tasks.add(task);

        service.retryTask(7L);

        assertThat(tasks.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void retryTaskRejectsUnknown() {
        assertThatThrownBy(() -> service.retryTask(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task does not exist");
    }

    @Test
    void reparseDocumentResetsDocumentStatusToPendingAndRequeuesTask() {
        var document = new KnowledgeDocument();
        document.setId(10L);
        document.setKnowledgeBaseId(1L);
        document.setStatus(3);
        documents.add(document);
        var task = new KnowledgeTask();
        task.setId(7L);
        task.setKnowledgeBaseId(1L);
        task.setDocumentId(10L);
        task.setTaskType("PARSE_AND_INDEX");
        task.setStatus("COMPLETED");
        tasks.add(task);

        boolean result = service.reparseDocument(10L);

        assertThat(result).isTrue();
        assertThat(documents.get(0).getStatus()).isEqualTo(0);
        assertThat(tasks.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void reparseDocumentSkipsInFlightDocument() {
        var document = new KnowledgeDocument();
        document.setId(10L);
        document.setKnowledgeBaseId(1L);
        document.setStatus(1);
        documents.add(document);

        boolean result = service.reparseDocument(10L);

        assertThat(result).isFalse();
        assertThat(documents.get(0).getStatus()).isEqualTo(1);
    }

    @Test
    void bindKnowledgeBasesStoresOnlyExistingBases() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");

        service.bindKnowledgeBases("demo", List.of(1L));

        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).getAgentKey()).isEqualTo("demo");
        assertThat(bindings.get(0).getKnowledgeBaseId()).isEqualTo(1L);
    }

    @Test
    void bindKnowledgeBasesRejectsMissingBase() {
        assertThatThrownBy(() -> service.bindKnowledgeBases("demo", List.of(99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void getKnowledgeBindingsReturnsBoundBaseIds() {
        var binding = new AgentKnowledgeBinding();
        binding.setAgentKey("demo");
        binding.setKnowledgeBaseId(1L);
        bindings.add(binding);

        assertThat(service.getKnowledgeBindings("demo")).containsExactly(1L);
    }

    private static KnowledgeBaseBo validBo(String name, Long embeddingModelId, Long storeId, int dimension) {
        var bo = new KnowledgeBaseBo();
        bo.setName(name);
        bo.setEmbeddingModelId(embeddingModelId);
        bo.setVectorStoreInstanceId(storeId);
        bo.setDimensionOfVectorModel(dimension);
        return bo;
    }

    private void seedKnowledgeBase(Long id, String name, String status) {
        var base = new KnowledgeBase();
        base.setId(id);
        base.setName(name);
        base.setStatus(status);
        knowledgeBases.add(base);
    }

    private void seedStore(Long id, String name, int category, int type) {
        var store = new StoreInstance();
        store.setId(id);
        store.setName(name);
        store.setCategory(category);
        store.setType(type);
        store.setStatus(1);
        stores.add(store);
    }

    private static <T> T findById(List<T> list, Object id) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.get(0) instanceof KnowledgeBase base) {
            return (T) list.stream().filter(b -> ((KnowledgeBase) b).getId().equals(id)).findFirst().orElse(null);
        }
        if (list.get(0) instanceof KnowledgeDocument document) {
            return (T) list.stream().filter(d -> ((KnowledgeDocument) d).getId().equals(id)).findFirst().orElse(null);
        }
        if (list.get(0) instanceof KnowledgeTask task) {
            return (T) list.stream().filter(t -> ((KnowledgeTask) t).getId().equals(id)).findFirst().orElse(null);
        }
        if (list.get(0) instanceof StoreInstance store) {
            return (T) list.stream().filter(s -> ((StoreInstance) s).getId().equals(id)).findFirst().orElse(null);
        }
        return null;
    }
}
