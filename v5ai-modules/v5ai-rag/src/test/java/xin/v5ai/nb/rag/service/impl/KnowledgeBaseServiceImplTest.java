package xin.v5ai.nb.rag.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.UrlContentFetcher;
import xin.v5ai.nb.rag.VectorStore;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.KnowledgeTask;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.mapper.AgentKnowledgeBindingMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeTaskMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeBaseServiceImpl} 的 Mockito 单测：mock Mapper + 假 VectorStore/UrlContentFetcher。
 *
 * @author ZYW
 * @since 2026-08-22
 */
class KnowledgeBaseServiceImplTest {

    private final List<KnowledgeBase> knowledgeBases = new ArrayList<>();
    private final List<KnowledgeDocument> documents = new ArrayList<>();
    private final List<KnowledgeTask> tasks = new ArrayList<>();
    private final List<AgentKnowledgeBinding> bindings = new ArrayList<>();

    private KnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境没有 MyBatis 启动流程，这里手动初始化实体 TableInfo，
        // 使服务内 LambdaUpdateWrapper/LambdaQueryWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeTask.class);
        TableInfoHelper.initTableInfo(assistant, AgentKnowledgeBinding.class);

        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeTaskMapper taskMapper = mock(KnowledgeTaskMapper.class);
        AgentKnowledgeBindingMapper bindingMapper = mock(AgentKnowledgeBindingMapper.class);
        VectorStore vectorStore = mock(VectorStore.class);
        UrlContentFetcher urlFetcher = mock(UrlContentFetcher.class);

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
            });
            return 1;
        }).when(knowledgeBaseMapper).updateById(any(KnowledgeBase.class));

        doAnswer(inv -> {
            var document = inv.getArgument(0, KnowledgeDocument.class);
            document.setId((long) (documents.size() + 1) * 10L);
            documents.add(document);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));
        when(documentMapper.selectById(any())).thenAnswer(inv -> findById(documents, inv.getArgument(0)));

        doAnswer(inv -> {
            var task = inv.getArgument(0, KnowledgeTask.class);
            task.setId((long) (tasks.size() + 1));
            tasks.add(task);
            return 1;
        }).when(taskMapper).insert(any(KnowledgeTask.class));
        when(taskMapper.selectById(any())).thenAnswer(inv -> findById(tasks, inv.getArgument(0)));
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

        when(urlFetcher.fetch("https://example.com/doc")).thenReturn("hello world");

        service = new KnowledgeBaseServiceImpl(knowledgeBaseMapper, documentMapper, taskMapper,
                bindingMapper, vectorStore, urlFetcher);
    }

    @Test
    void createKnowledgeBaseAssignsIdAndActiveStatus() {
        var bo = new KnowledgeBaseBo();
        bo.setName("Docs");
        bo.setDescription("product docs");

        var created = service.createKnowledgeBase(bo);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("Docs");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(knowledgeBases).hasSize(1);
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

        var document = service.uploadDocument(1L, "guide.md", DocumentFileType.MARKDOWN,
                "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8), "guide.md");

        assertThat(document.getId()).isEqualTo(10L);
        assertThat(document.getStatus()).isEqualTo("PENDING");
        assertThat(document.getFileType()).isEqualTo("MARKDOWN");
        assertThat(documents.get(0).getContent()).isEqualTo("hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(tasks.get(0).getTaskType()).isEqualTo("PARSE_AND_INDEX");
        assertThat(tasks.get(0).getMaxAttempts()).isEqualTo(3);
        assertThat(tasks.get(0).getAttemptCount()).isZero();
    }

    @Test
    void uploadDocumentRejectsUnknownKnowledgeBase() {
        assertThatThrownBy(() -> service.uploadDocument(99L, "a.txt", DocumentFileType.TXT,
                new byte[]{1}, "a.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void uploadDocumentWithLegacySnowflakeIdIncludesMigrationHint() {
        assertThatThrownBy(() -> service.uploadDocument(2088154694704398300L, "a.txt", DocumentFileType.TXT,
                new byte[]{1}, "a.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist")
                .hasMessageContaining("JavaScript 安全整数");
    }

    @Test
    void importUrlFetchesContentAndStoresUrlDocument() {
        seedKnowledgeBase(1L, "docs", "ACTIVE");

        var document = service.importUrl(1L, null, "https://example.com/doc");

        assertThat(document.getTitle()).isEqualTo("https://example.com/doc");
        assertThat(document.getFileType()).isEqualTo("URL");
        assertThat(document.getSource()).isEqualTo("https://example.com/doc");
        assertThat(documents.get(0).getContent()).isEqualTo("hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    private void seedKnowledgeBase(Long id, String name, String status) {
        var base = new KnowledgeBase();
        base.setId(id);
        base.setName(name);
        base.setStatus(status);
        knowledgeBases.add(base);
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
        return null;
    }
}
