package xin.v5ai.nb.worker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.core.chunker.DocumentChunker;
import xin.v5ai.nb.rag.core.parser.DocumentParseCoordinator;
import xin.v5ai.nb.rag.core.parser.ParsedDocument;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.KnowledgeTask;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;
import xin.v5ai.nb.rag.service.IKnowledgeDocumentService;
import xin.v5ai.nb.rag.service.IKnowledgeTaskService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeIndexingServiceTest {

    @Test
    void loadsKnowledgeBaseBeforeParsingAndPersistsStructuredParseResult() {
        var taskService = mock(IKnowledgeTaskService.class);
        var documentService = mock(IKnowledgeDocumentService.class);
        var knowledgeBaseService = mock(IKnowledgeBaseService.class);
        var contentStore = mock(KnowledgeDocumentContentStore.class);
        var coordinator = mock(DocumentParseCoordinator.class);
        var chunker = mock(DocumentChunker.class);
        var embedding = mock(EmbeddingClient.class);
        var resolver = mock(VectorStoreResolver.class);
        var chunkService = mock(IKnowledgeChunkService.class);
        var vectorStore = mock(VectorStore.class);

        var task = new KnowledgeTask();
        task.setId(10L); task.setDocumentId(20L); task.setAttemptCount(0); task.setStatus("PENDING");
        var document = new KnowledgeDocument();
        document.setId(20L); document.setKnowledgeBaseId(30L); document.setTitle("guide.md");
        document.setFileType("MARKDOWN"); document.setStatus(0);
        var base = new KnowledgeBaseVo();
        base.setId(30L); base.setVectorStoreInstanceId(40L);
        base.setConfig(xin.v5ai.nb.rag.core.config.RagConfigDO.builder()
                .parseParams(xin.v5ai.nb.rag.core.config.RagConfigDO.ParseParams.builder().engine("docling").build()).build());
        when(taskService.findById(10L)).thenReturn(task);
        when(documentService.findById(20L)).thenReturn(document);
        when(knowledgeBaseService.getKnowledgeBase(30L)).thenReturn(base);
        when(contentStore.loadContent(20L)).thenReturn("source".getBytes());
        when(coordinator.parse(any())).thenReturn(ParsedDocument.builder().engine("default").markdown("parsed text").structuredJson("{\"fallback\":true}").build());
        when(chunker.chunk(eq("parsed text"), any())).thenReturn(List.of("parsed text"));
        when(embedding.embed(eq("parsed text"), isNull(), isNull())).thenReturn(List.of(0.1f));
        when(resolver.vectorStore(40L)).thenReturn(vectorStore);
        when(chunkService.saveBatch(any())).thenReturn(true);

        new KnowledgeIndexingService(taskService, documentService, knowledgeBaseService, contentStore, coordinator,
                chunker, embedding, resolver, chunkService).processTask(10L);

        assertThat(document.getParsedText()).isEqualTo("parsed text");
        assertThat(document.getParseEngine()).isEqualTo("default");
        assertThat(document.getParseDiagnostics()).contains("fallback");
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        verify(coordinator).parse(argThat(request -> "docling".equals(request.params().getEngine())
                && request.documentId().equals(20L) && request.knowledgeBaseId().equals(30L)));
        verify(vectorStore).store(any());
    }
}
