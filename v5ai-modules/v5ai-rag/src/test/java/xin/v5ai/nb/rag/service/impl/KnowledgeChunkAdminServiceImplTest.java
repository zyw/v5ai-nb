package xin.v5ai.nb.rag.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.core.tokenizer.TextTokenizer;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.bo.KbChunkAddBo;
import xin.v5ai.nb.rag.domain.bo.KbChunkEditBo;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeChunkAdminServiceImpl} 的 Mockito 单测：
 * 手工切片的新增（归属校验 + 追加序号 + 业务行/向量双写）、编辑重嵌、删除与内容校验。
 *
 * @author ZYW
 * @since 2026-09-06
 */
class KnowledgeChunkAdminServiceImplTest {

    private KnowledgeChunkMapper chunkMapper;
    private KnowledgeDocumentMapper documentMapper;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private EmbeddingClient embeddingClient;
    private VectorStoreResolver vectorStoreResolver;
    private TextTokenizer textTokenizer;
    private VectorStore vectorStore;
    private KnowledgeChunkAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        chunkMapper = mock(KnowledgeChunkMapper.class);
        documentMapper = mock(KnowledgeDocumentMapper.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        embeddingClient = mock(EmbeddingClient.class);
        vectorStoreResolver = mock(VectorStoreResolver.class);
        vectorStore = mock(VectorStore.class);
        textTokenizer = mock(TextTokenizer.class);
        when(textTokenizer.tokenizeForIndex(any())).thenReturn(List.of("新", "切片", "内容"));
        service = new KnowledgeChunkAdminServiceImpl(
                chunkMapper, documentMapper, knowledgeBaseMapper, embeddingClient, vectorStoreResolver,
                textTokenizer);
    }

    private KnowledgeBase base(Long id) {
        var base = new KnowledgeBase();
        base.setId(id);
        base.setVectorStoreInstanceId(10L);
        base.setEmbeddingModelId(1L);
        base.setDimensionOfVectorModel(2);
        return base;
    }

    private KnowledgeDocument document(Long id, Long kbId, String title) {
        // 已进入索引终态（3-处理完成），便于聚焦各用例自身的断言
        return document(id, kbId, title, 3);
    }

    private KnowledgeDocument document(Long id, Long kbId, String title, Integer status) {
        var document = new KnowledgeDocument();
        document.setId(id);
        document.setKnowledgeBaseId(kbId);
        document.setTitle(title);
        document.setStatus(status);
        return document;
    }

    private KnowledgeChunk chunk(Long id, Long kbId, Long documentId, int index, String content) {
        var chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setKnowledgeBaseId(kbId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setVectorId("vec-" + id);
        return chunk;
    }

    @Test
    void addChunk_appendsIndexAndStoresBusinessRowAndVector() {
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册"));
        when(chunkMapper.nextChunkIndex(1L, 2L)).thenReturn(7);
        when(embeddingClient.embed("新切片内容", 1L, 2)).thenReturn(List.of(0.1f, 0.2f));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);

        KnowledgeChunk saved = chunk(88L, 1L, 2L, 7, "新切片内容");
        when(chunkMapper.selectOne(any())).thenReturn(saved);

        var vo = service.addChunk(1L, new KbChunkAddBo(2L, "新切片内容"));

        assertThat(vo.getId()).isEqualTo(88L);
        assertThat(vo.getDocumentTitle()).isEqualTo("操作手册");
        // 追加到文档末尾：index = max + 1
        verify(chunkMapper).nextChunkIndex(1L, 2L);
        // 业务行先落（batchSaveChunks，含 vector_id、metadata、关键词分词索引），再回查
        var rows = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(chunkMapper).batchSaveChunks(rows.capture());
        @SuppressWarnings("unchecked")
        List<KnowledgeChunk> savedRows = (List<KnowledgeChunk>) rows.getValue();
        assertThat(savedRows.get(0).getKeywordTokens()).containsExactly("新", "切片", "内容");
        verify(chunkMapper).selectOne(any());
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore).store(captor.capture());
        @SuppressWarnings("unchecked")
        VectorStore.VectorChunk stored = ((List<VectorStore.VectorChunk>) captor.getValue()).get(0);
        assertThat(stored.vectorId()).isNotBlank();
        assertThat(stored.chunkIndex()).isEqualTo(7);
        assertThat(stored.content()).isEqualTo("新切片内容");
        assertThat(stored.metadata()).contains("manual");
    }

    @Test
    void addChunk_rejectsDocumentOfAnotherBase() {
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(documentMapper.selectById(9L)).thenReturn(document(9L, 99L, "别人的文档"));

        assertThatThrownBy(() -> service.addChunk(1L, new KbChunkAddBo(9L, "内容")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不属于该知识库");
    }

    @Test
    void addChunk_rejectsBlankContent() {
        assertThatThrownBy(() -> service.addChunk(1L, new KbChunkAddBo(2L, "   ")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void updateChunk_reembedsBusinessRowAndVector() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 1L, 2L, 3, "旧内容"));
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册"));
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(embeddingClient.embed("新内容", 1L, 2)).thenReturn(List.of(0.5f, 0.6f));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);

        KnowledgeChunk refreshed = chunk(5L, 1L, 2L, 3, "新内容");
        when(chunkMapper.selectById(eq(5L))).thenReturn(refreshed, refreshed);

        var vo = service.updateChunk(1L, 5L, new KbChunkEditBo("新内容"));

        var updated = org.mockito.ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper).updateById(updated.capture());
        // 改内容同步刷新关键词分词索引（否则 BM25 仍按旧内容召回）
        assertThat(updated.getValue().getKeywordTokens()).containsExactly("新", "切片", "内容");
        // 集合内向量：先按 vector_id 删旧，再写新
        verify(vectorStore).deleteByVectorId(1L, "vec-5");
        verify(vectorStore).store(anyList());
        assertThat(vo.getContent()).isEqualTo("新内容");
        assertThat(vo.getDocumentTitle()).isEqualTo("操作手册");
    }

    @Test
    void deleteChunk_removesRowAndVector() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 1L, 2L, 3, "内容"));
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);

        service.deleteChunk(1L, 5L);

        verify(vectorStore).deleteByVectorId(1L, "vec-5");
        verify(chunkMapper).deleteById(5L);
    }

    @Test
    void addChunk_rejectsDocumentNotIndexedYet() {
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册", 2));

        assertThatThrownBy(() -> service.addChunk(1L, new KbChunkAddBo(2L, "内容")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未完成索引");
        verify(chunkMapper, never()).batchSaveChunks(anyList());
    }

    @Test
    void updateChunk_rejectsDocumentNotIndexedYet() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 1L, 2L, 3, "旧内容"));
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册", 1));

        assertThatThrownBy(() -> service.updateChunk(1L, 5L, new KbChunkEditBo("新内容")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未完成索引");
        verify(chunkMapper, never()).updateById(any(KnowledgeChunk.class));
    }

    @Test
    void deleteChunk_rejectsDocumentNotIndexedYet() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 1L, 2L, 3, "内容"));
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册", 0));

        assertThatThrownBy(() -> service.deleteChunk(1L, 5L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未完成索引");
        verify(chunkMapper, never()).deleteById(5L);
        verify(vectorStore, never()).deleteByVectorId(any(), any());
    }

    @Test
    void addChunk_incrementsOwningDocumentChunkCount() {
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        var document = document(2L, 1L, "操作手册");
        document.setChunkCount(6);
        when(documentMapper.selectById(2L)).thenReturn(document);
        when(chunkMapper.nextChunkIndex(1L, 2L)).thenReturn(7);
        when(embeddingClient.embed("新切片内容", 1L, 2)).thenReturn(List.of(0.1f, 0.2f));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);
        when(chunkMapper.selectOne(any())).thenReturn(chunk(88L, 1L, 2L, 7, "新切片内容"));

        service.addChunk(1L, new KbChunkAddBo(2L, "新切片内容"));

        var patch = org.mockito.ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).updateById(patch.capture());
        assertThat(patch.getValue().getId()).isEqualTo(2L);
        assertThat(patch.getValue().getChunkCount()).isEqualTo(7);
    }

    @Test
    void deleteChunk_decrementsOwningDocumentChunkCount() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 1L, 2L, 3, "内容"));
        var document = document(2L, 1L, "操作手册");
        document.setChunkCount(4);
        when(documentMapper.selectById(2L)).thenReturn(document);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);

        service.deleteChunk(1L, 5L);

        var patch = org.mockito.ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).updateById(patch.capture());
        assertThat(patch.getValue().getChunkCount()).isEqualTo(3);
    }

    @Test
    void deleteChunk_floorsChunkCountAtZero() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 1L, 2L, 3, "内容"));
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册"));
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);

        service.deleteChunk(1L, 5L);

        var patch = org.mockito.ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).updateById(patch.capture());
        assertThat(patch.getValue().getChunkCount()).isZero();
    }

    @Test
    void updateChunk_doesNotTouchChunkCount() {
        when(documentMapper.selectById(2L)).thenReturn(document(2L, 1L, "操作手册"));
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(base(1L));
        when(embeddingClient.embed("新内容", 1L, 2)).thenReturn(List.of(0.5f, 0.6f));
        when(vectorStoreResolver.vectorStore(10L)).thenReturn(vectorStore);
        var refreshed = chunk(5L, 1L, 2L, 3, "新内容");
        when(chunkMapper.selectById(eq(5L))).thenReturn(chunk(5L, 1L, 2L, 3, "旧内容"), refreshed);

        service.updateChunk(1L, 5L, new KbChunkEditBo("新内容"));

        verify(documentMapper, never()).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void deleteChunk_rejectsForeignChunk() {
        when(chunkMapper.selectById(5L)).thenReturn(chunk(5L, 99L, 2L, 3, "内容"));

        assertThatThrownBy(() -> service.deleteChunk(1L, 5L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不属于该知识库");
        verify(vectorStore, never()).deleteByVectorId(any(), any());
    }
}
