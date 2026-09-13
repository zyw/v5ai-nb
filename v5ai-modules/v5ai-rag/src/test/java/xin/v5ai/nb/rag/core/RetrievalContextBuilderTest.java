package xin.v5ai.nb.rag.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.dto.RetrievalCitation;
import xin.v5ai.nb.rag.core.dto.RetrievalResult;
import xin.v5ai.nb.rag.core.store.KeywordStore;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.service.IAgentKnowledgeBindingService;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeDocumentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link RetrievalContextBuilder} 运行时检索单测：默认快速路径与显式融合路径（D1/D2/D3 语义）。
 *
 * @author ZYW
 * @since 2026-09-19
 */
class RetrievalContextBuilderTest {

    private static final long STORE_ID = 9L;

    private IAgentKnowledgeBindingService bindingService;
    private IKnowledgeBaseService baseService;
    private VectorStoreResolver resolver;
    private EmbeddingClient embeddingClient;
    private IKnowledgeDocumentService documentService;
    private RetrievalHitEnricher hitEnricher;
    private VectorStore vectorStore;
    private RetrievalContextBuilder builder;

    @BeforeEach
    void setUp() {
        bindingService = mock(IAgentKnowledgeBindingService.class);
        baseService = mock(IKnowledgeBaseService.class);
        resolver = mock(VectorStoreResolver.class);
        embeddingClient = mock(EmbeddingClient.class);
        documentService = mock(IKnowledgeDocumentService.class);
        // 同时实现向量 + 关键词双接口的存储（如默认 PgVectorStore 的行为）
        vectorStore = mock(VectorStore.class, withSettings().extraInterfaces(KeywordStore.class));
        when(resolver.vectorStore(STORE_ID)).thenReturn(vectorStore);
        when(embeddingClient.embed(any(), eq(1L), eq(3))).thenReturn(List.of(0.1f));
        hitEnricher = mock(RetrievalHitEnricher.class);
        when(hitEnricher.enrich(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        builder = new RetrievalContextBuilder(
                bindingService, baseService, resolver, embeddingClient, documentService, hitEnricher);
    }

    private KnowledgeBaseVo base(Long id) {
        return base(id, null);
    }

    private KnowledgeBaseVo base(Long id, RagConfigDO.SearchParams searchParams) {
        KnowledgeBaseVo base = new KnowledgeBaseVo();
        base.setId(id);
        base.setStatus("ACTIVE");
        base.setEmbeddingModelId(1L);
        base.setDimensionOfVectorModel(3);
        base.setVectorStoreInstanceId(STORE_ID);
        if (searchParams != null) {
            base.setConfig(RagConfigDO.builder().searchParams(searchParams).build());
        }
        return base;
    }

    private VectorStore.RetrievalHit hit(Long kbId, Long docId, int index, String content, double score) {
        return new VectorStore.RetrievalHit("v" + kbId + "-" + docId + "-" + index, kbId, docId, index, content, "{}", score);
    }

    private void stubBases(List<KnowledgeBaseVo> bases) {
        when(bindingService.findByAgentKey("agent-a")).thenReturn(bases.stream().map(KnowledgeBaseVo::getId).toList());
        for (KnowledgeBaseVo base : bases) {
            when(baseService.getKnowledgeBase(base.getId())).thenReturn(base);
        }
    }

    @Test
    void build_keepsFastPathWhenNoExplicitFusionConfig() {
        stubBases(List.of(base(1L)));
        when(vectorStore.search(anyList(), any(), eq(4))).thenReturn(List.of(
                hit(1L, 1L, 0, "向量命中", 0.9)));

        RetrievalResult result = builder.build("agent-a", "问题");

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.citations()).extracting(RetrievalCitation::documentId).containsExactly(1L);
        // 引用必须带标题（文档行缺失时回退 doc-{id}），否则前端无从显示「引用了哪个文件」
        assertThat(result.citations()).extracting(RetrievalCitation::documentTitle).containsExactly("doc-1");
        // 快速路径：有向量命中不触发关键词检索（默认不引入双路检索）
        verify(vectorStore).search(anyList(), any(), eq(4));
        verify((KeywordStore) vectorStore, never()).keywordSearch(anyList(), eq("问题"), eq(4));
    }

    @Test
    void build_fallsBackToKeywordOnlyWhenVectorEmptyOnFastPath() {
        stubBases(List.of(base(1L)));
        when(vectorStore.search(anyList(), any(), eq(4))).thenReturn(List.of());
        when(((KeywordStore) vectorStore).keywordSearch(anyList(), eq("问题"), eq(4))).thenReturn(List.of(
                hit(1L, 2L, 0, "关键词命中", 1.0)));

        RetrievalResult result = builder.build("agent-a", "问题");

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.citations()).extracting(RetrievalCitation::documentId).containsExactly(2L);
    }

    @Test
    void build_runsFusionWhenSingleKbExplicitlyConfigured() {
        RagConfigDO.SearchParams sp = RagConfigDO.SearchParams.builder()
                .fusionStrategy("RRF").rrfK(40).build();
        stubBases(List.of(base(1L, sp)));
        // 候选数 = max(4*3, 30) = 30：向量路 A(rank1) B(rank2)，关键词路 B(rank1)
        when(vectorStore.search(anyList(), any(), eq(30))).thenReturn(List.of(
                hit(1L, 1L, 0, "向量A", 0.9),
                hit(1L, 2L, 0, "向量B", 0.8)));
        when(((KeywordStore) vectorStore).keywordSearch(anyList(), eq("问题"), eq(30))).thenReturn(List.of(
                hit(1L, 2L, 0, "关键词B", 1.0)));

        RetrievalResult result = builder.build("agent-a", "问题");

        verify(vectorStore).search(anyList(), any(), eq(30));
        verify((KeywordStore) vectorStore).keywordSearch(anyList(), eq("问题"), eq(30));
        // RRF(k=40)：B = 1/41 + 1/42 > A = 1/41 → 双路融合排序（不是仅取向量路）
        assertThat(result.citations()).extracting(RetrievalCitation::documentId).containsExactly(2L, 1L);
        assertThat(result.citations().get(0).score())
                .isCloseTo(1.0 / 41 + 1.0 / 42, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void build_fallsBackWhenMultiKbConfigsDiverge() {
        stubBases(List.of(
                base(1L, RagConfigDO.SearchParams.builder().fusionStrategy("RRF").rrfK(40).build()),
                base(2L, RagConfigDO.SearchParams.builder().fusionStrategy("RRF").rrfK(200).build())));
        when(vectorStore.search(anyList(), any(), eq(4))).thenReturn(List.of(
                hit(1L, 1L, 0, "命中", 0.9),
                hit(2L, 1L, 0, "命中2", 0.7)));

        RetrievalResult result = builder.build("agent-a", "问题");

        // 配置不一致 → 回退默认快速路径（向量 top4，不双路、不融合）
        verify(vectorStore).search(anyList(), any(), eq(4));
        verify((KeywordStore) vectorStore, never()).keywordSearch(anyList(), eq("问题"), eq(4));
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void build_runsFusionWhenAllKbsShareIdenticalConfig() {
        stubBases(List.of(
                base(1L, RagConfigDO.SearchParams.builder().fusionStrategy("RRF").rrfK(40).build()),
                base(2L, RagConfigDO.SearchParams.builder().fusionStrategy("RRF").rrfK(40).build())));
        when(vectorStore.search(anyList(), any(), eq(30))).thenReturn(List.of(
                hit(1L, 1L, 0, "向量A", 0.9),
                hit(2L, 1L, 0, "向量C", 0.6)));
        when(((KeywordStore) vectorStore).keywordSearch(anyList(), eq("问题"), eq(30))).thenReturn(List.of(
                hit(2L, 1L, 0, "关键词C", 1.0)));

        RetrievalResult result = builder.build("agent-a", "问题");

        // 全库显式 RRF 且一致 → 双路融合（候选 30）
        verify(vectorStore).search(anyList(), any(), eq(30));
        verify((KeywordStore) vectorStore).keywordSearch(anyList(), eq("问题"), eq(30));
        // C(kb2) 向量 rank2 + 关键词 rank1 → 融合分高于仅向量的 A(kb1)
        assertThat(result.citations()).extracting(RetrievalCitation::content)
                .containsExactly("向量C", "向量A");
    }
}
