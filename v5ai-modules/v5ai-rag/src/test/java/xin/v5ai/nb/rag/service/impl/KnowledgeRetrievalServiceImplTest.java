package xin.v5ai.nb.rag.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.RetrievalHitEnricher;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.store.KeywordStore;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.bo.KbRetrieveBo;
import xin.v5ai.nb.rag.domain.vo.KbHitVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link KnowledgeRetrievalServiceImpl} 的 Mockito 单测：
 * 默认值补齐、RRF 融合排序、VECTOR 阈值过滤与问题改写回退（半真语义）。
 *
 * @author ZYW
 * @since 2026-09-06
 */
class KnowledgeRetrievalServiceImplTest {

    private IKnowledgeBaseService baseService;
    private EmbeddingClient embeddingClient;
    private VectorStoreResolver resolver;
    private KnowledgeDocumentMapper documentMapper;
    private ModelChatClient chatClient;
    private VectorStore vectorStore;
    private RetrievalHitEnricher hitEnricher;
    private KnowledgeRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        baseService = mock(IKnowledgeBaseService.class);
        embeddingClient = mock(EmbeddingClient.class);
        resolver = mock(VectorStoreResolver.class);
        documentMapper = mock(KnowledgeDocumentMapper.class);
        chatClient = mock(ModelChatClient.class);
        // 同时实现向量 + 关键词双接口的存储（如默认 PgVectorStore 的行为）
        vectorStore = mock(VectorStore.class, withSettings().extraInterfaces(KeywordStore.class));
        // 回填器在单测中透传（构造点已带完整 content，无需回业务表）
        hitEnricher = mock(RetrievalHitEnricher.class);
        when(hitEnricher.enrich(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new KnowledgeRetrievalServiceImpl(
                baseService, embeddingClient, resolver, documentMapper, chatClient, hitEnricher);
    }

    private KnowledgeBaseVo base(Long id) {
        var base = new KnowledgeBaseVo();
        base.setId(id);
        base.setStatus("ACTIVE");
        base.setEmbeddingModelId(1L);
        base.setDimensionOfVectorModel(3);
        return base;
    }

    private void stubDocs() {
        var doc1 = new KnowledgeDocument();
        doc1.setId(1L);
        doc1.setTitle("单晶硅拉晶");
        var doc2 = new KnowledgeDocument();
        doc2.setId(2L);
        doc2.setTitle("导游面试");
        when(documentMapper.selectBatchIds(anyList())).thenReturn(List.of(doc1, doc2));
    }

    private VectorStore.RetrievalHit vectorHit(Long docId, int index, String content, double score) {
        return new VectorStore.RetrievalHit("v" + docId + "-" + index, 1L, docId, index, content, "{}", score);
    }

    private VectorStore.RetrievalHit keywordHit(Long docId, int index, String content) {
        return keywordHit(docId, index, content, 1.0);
    }

    private VectorStore.RetrievalHit keywordHit(Long docId, int index, String content, double score) {
        return new VectorStore.RetrievalHit("v" + docId + "-" + index, 1L, docId, index, content, "{}", score);
    }

    @Test
    void retrieve_usesRrfByDefaultAndTrimsToResultCount() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));
        when(resolver.vectorStore(null)).thenReturn(vectorStore);
        when(embeddingClient.embed("硅片", 1L, 3)).thenReturn(List.of(0.1f));
        when(vectorStore.search(anyList(), any(), eq(30))).thenReturn(List.of(
                vectorHit(1L, 0, "向量命中A", 0.9),
                vectorHit(1L, 1, "向量命中B", 0.8)));
        when(((KeywordStore) vectorStore).keywordSearch(anyList(), eq("硅片"), eq(30)))
                .thenReturn(List.of(keywordHit(1L, 1, "关键词命中B")));
        stubDocs();

        List<KbHitVo> hits = service.retrieve(1L, new KbRetrieveBo("硅片", 1, null, null, null, null, null, null, null));

        // resultCount=1 → 候选数 = max(1*3, 30) = 30；RRF 下 B 向量 rank2 + 关键词 rank1 分更高 → 取 top1（内容按向量路去重）
        verify(vectorStore).search(anyList(), any(), eq(30));
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).content()).isEqualTo("向量命中B");
        assertThat(hits.get(0).score()).isPositive();
    }

    @Test
    void retrieve_vectorStrategy_appliesThresholdOnSimilarity() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));
        when(resolver.vectorStore(null)).thenReturn(vectorStore);
        when(embeddingClient.embed("问题", 1L, 3)).thenReturn(List.of(0.1f));
        when(vectorStore.search(anyList(), any(), eq(30))).thenReturn(List.of(
                vectorHit(1L, 0, "高相关", 0.9),
                vectorHit(2L, 0, "中相关", 0.8),
                vectorHit(2L, 1, "低相关", 0.5)));
        stubDocs();

        List<KbHitVo> hits = service.retrieve(1L,
                new KbRetrieveBo("问题", 10, null, true, 0.85, "VECTOR", null, null, null));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).content()).isEqualTo("高相关");
        assertThat(hits.get(0).documentTitle()).isEqualTo("单晶硅拉晶");
    }

    @Test
    void retrieve_weightedSumNormalizesChannelsBeforeWeighting() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));
        when(resolver.vectorStore(null)).thenReturn(vectorStore);
        when(embeddingClient.embed("加权", 1L, 3)).thenReturn(List.of(0.1f));
        when(vectorStore.search(anyList(), any(), eq(30))).thenReturn(List.of(
                vectorHit(1L, 0, "仅向量A", 0.9),
                vectorHit(1L, 1, "两路B", 0.5),
                vectorHit(1L, 2, "低向量C", 0.4)));
        when(((KeywordStore) vectorStore).keywordSearch(anyList(), eq("加权"), eq(30)))
                .thenReturn(List.of(keywordHit(1L, 1, "两路B", 4.0), keywordHit(2L, 0, "仅关键词D", 3.5)));
        stubDocs();

        List<KbHitVo> hits = service.retrieve(1L,
                new KbRetrieveBo("加权", 10, null, null, null, "WEIGHTED_SUM", null, null, 0.5));

        // 两路先按通道 min-max 归一（量纲不同）再加权：
        // 向量 0.9/0.5/0.4 → 1.0/0.2/0.0；关键词 4.0/3.5 → 1.0/0.0
        // B = 0.5×0.2 + 0.5×1.0 = 0.6；A = 0.5×1.0 = 0.5；C = 0；D = 0.5×0.0 = 0
        assertThat(hits).extracting(KbHitVo::content)
                .containsExactly("两路B", "仅向量A", "低向量C", "仅关键词D");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
        assertThat(hits.get(1).score()).isGreaterThan(hits.get(2).score());
    }

    @Test
    void retrieve_rewriteFailureFallsBackToOriginalQuery() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));
        when(resolver.vectorStore(null)).thenReturn(vectorStore);
        when(embeddingClient.embed("原问题", 1L, 3)).thenReturn(List.of(0.1f));
        when(vectorStore.search(anyList(), any(), eq(90))).thenReturn(List.of(vectorHit(1L, 0, "命中", 0.7)));
        when(chatClient.chatText(eq(7L), any(), any())).thenThrow(new RuntimeException("模型不可用"));
        stubDocs();

        List<KbHitVo> hits = service.retrieve(1L,
                new KbRetrieveBo("原问题", 30, true, null, null, null, null, 7L, null));

        assertThat(hits).hasSize(1);
        // 改写失败不中断检索：仍按原问题走向量路（嵌入模型/维度随 KB 冻结）
        ArgumentCaptor<String> embedArg = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(embedArg.capture(), eq(1L), eq(3));
        assertThat(embedArg.getValue()).isEqualTo("原问题");
    }

    @Test
    void retrieve_rejectsBlankQuery() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));

        assertThatThrownBy(() -> service.retrieve(1L, new KbRetrieveBo("  ", null, null, null, null, null, null, null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请输入检索内容");
    }

    @Test
    void retrieve_rejectsUnknownFusionStrategy() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));

        assertThatThrownBy(() -> service.retrieve(1L,
                new KbRetrieveBo("问题", null, null, null, null, "MIX", null, null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的融合策略");
    }

    @Test
    void retrieve_rejectsUnknownFusionStrategyFromStoredConfig() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(baseWithSearchParams(1L,
                RagConfigDO.SearchParams.builder().fusionStrategy("MIX").build()));

        assertThatThrownBy(() -> service.retrieve(1L,
                new KbRetrieveBo("问题", null, null, null, null, null, null, null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的融合策略");
    }

    @Test
    void retrieve_rejectsRrfKOutOfRangeOnRrfStrategy() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));
        // fusionStrategy=null → 默认 RRF；rrfK 越界（0 / 201）拒绝
        assertThatThrownBy(() -> service.retrieve(1L,
                new KbRetrieveBo("问题", null, null, null, null, null, 0, null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("RRF K 值须在 1~200 之间");
        assertThatThrownBy(() -> service.retrieve(1L,
                new KbRetrieveBo("问题", null, null, null, null, null, 201, null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("RRF K 值须在 1~200 之间");
    }

    @Test
    void retrieve_ignoresOutOfRangeRrfKOnNonRrfStrategy() {
        when(baseService.getKnowledgeBase(1L)).thenReturn(base(1L));
        when(resolver.vectorStore(null)).thenReturn(vectorStore);
        // WEIGHTED_SUM 下 rrfK 不参与融合，越界也不校验（仅 RRF 校验）
        List<KbHitVo> hits = service.retrieve(1L,
                new KbRetrieveBo("问题", 10, null, null, null, "WEIGHTED_SUM", 999, null, 0.5));

        assertThat(hits).isEmpty();
    }

    private KnowledgeBaseVo baseWithSearchParams(Long id, RagConfigDO.SearchParams searchParams) {
        KnowledgeBaseVo base = base(id);
        base.setConfig(RagConfigDO.builder().searchParams(searchParams).build());
        return base;
    }
}
