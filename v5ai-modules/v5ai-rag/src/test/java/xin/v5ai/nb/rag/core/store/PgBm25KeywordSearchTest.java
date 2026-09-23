package xin.v5ai.nb.rag.core.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.tokenizer.TextTokenizer;
import xin.v5ai.nb.rag.domain.KnowledgeChunkDO;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PgBm25KeywordSearch} 单测：无词项回退信号、候选/df/avgdl 组装、BM25 排序与纯函数性质。
 *
 * @author ZYW
 * @since 2026-09-09
 */
class PgBm25KeywordSearchTest {

    private KnowledgeChunkMapper chunkMapper;
    private TextTokenizer textTokenizer;
    private PgBm25KeywordSearch search;

    @BeforeEach
    void setUp() {
        chunkMapper = mock(KnowledgeChunkMapper.class);
        textTokenizer = mock(TextTokenizer.class);
        search = new PgBm25KeywordSearch(chunkMapper, textTokenizer);
        when(textTokenizer.tokenizeForSearch(anyString())).thenReturn(List.of("温度", "控制"));
    }

    private KnowledgeChunkDO row(long id, int index, String content, String... tokens) {
        var row = new KnowledgeChunkDO();
        row.setId(id);
        row.setVectorId("vec-" + id);
        row.setKnowledgeBaseId(1L);
        row.setDocumentId(id);
        row.setChunkIndex(index);
        row.setContent(content);
        row.setKeywordTokens(tokens.length == 0 ? null : tokens);
        return row;
    }

    private void stubCorpus(long docCount, double avgLength) {
        when(chunkMapper.countIndexedChunks(anyList())).thenReturn(docCount);
        when(chunkMapper.avgKeywordTokenLength(anyList())).thenReturn(avgLength);
        when(chunkMapper.countChunksByKeywordToken(anyList(), eq("温度"))).thenReturn(1L);
        when(chunkMapper.countChunksByKeywordToken(anyList(), eq("控制"))).thenReturn(2L);
    }

    @Test
    void emptySignalWhenQueryHasNoUsableTerms() {
        when(textTokenizer.tokenizeForSearch("的了的")).thenReturn(List.of());

        // Optional.empty 即「无法分词」：调用方据此回退 ILIKE 粗检，不返回空错
        assertThat(search.search(List.of(1L), "的了的", 5)).isEmpty();
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void emptyHitsWhenNoCandidateMatches() {
        when(chunkMapper.findChunksByKeywordTokens(anyList(), any(), any(), anyInt())).thenReturn(List.of());

        assertThat(search.search(List.of(1L), "温度控制", 5)).contains(List.of());
        // 无候选时不再查询 df/avgdl（省两次往返）
        verify(chunkMapper, never()).countIndexedChunks(anyList());
    }

    @Test
    void ranksByBm25AndSkipsUnindexedRows() {
        var high = row(1L, 0, "高温", "温度", "温度", "温度", "控制", "a", "b", "c", "d", "e", "f");
        var low = row(2L, 0, "常温", "温度", "控制", "a", "b", "c", "d", "e", "f", "g", "h");
        var stale = row(3L, 0, "未重建", new String[0]);
        when(chunkMapper.findChunksByKeywordTokens(anyList(), any(), any(), anyInt()))
                .thenReturn(List.of(low, stale, high));
        stubCorpus(10L, 10.0);

        List<VectorStore.RetrievalHit> hits = search.search(List.of(1L), "温度控制", 5).orElseThrow();

        // tf(温度)=3 的切片排在 tf=1 之前；无分词索引的存量行不参与（否则分数恒 0）
        assertThat(hits).extracting(VectorStore.RetrievalHit::documentId).containsExactly(1L, 2L);
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
        // 连续相关度：不再是 ILIKE 粗检的恒 1.0
        assertThat(hits.get(0).score()).isGreaterThan(0.0).isNotEqualTo(1.0);
        assertThat(hits.get(0).content()).isEqualTo("高温");
        assertThat(hits.get(0).vectorId()).isEqualTo("vec-1");
    }

    @Test
    void truncatesToTopK() {
        var first = row(1L, 0, "一", "温度", "控制");
        var second = row(2L, 0, "二", "温度", "控制");
        when(chunkMapper.findChunksByKeywordTokens(anyList(), any(), any(), anyInt()))
                .thenReturn(List.of(first, second));
        stubCorpus(10L, 10.0);

        assertThat(search.search(List.of(1L), "温度控制", 1).orElseThrow()).hasSize(1);
    }

    @Test
    void candidateLimitExceedsTopK() {
        when(chunkMapper.findChunksByKeywordTokens(anyList(), any(), any(), anyInt())).thenReturn(List.of());

        search.search(List.of(1L), "温度控制", 30);

        // 候选必须多于返回条数（截断先于计分会丢召回）：max(30×5, 200)
        verify(chunkMapper).findChunksByKeywordTokens(anyList(), any(), any(), eq(200));
    }

    @Test
    void scoresGrowWithTermFrequencyButSaturate() {
        double once = PgBm25KeywordSearch.score(1, 10, 10.0, 100, 10);
        double twice = PgBm25KeywordSearch.score(2, 10, 10.0, 100, 10);

        assertThat(twice).isGreaterThan(once);
        // tf 次线性饱和：翻倍词频的得分不到两倍
        assertThat(twice).isLessThan(2 * once);
    }

    @Test
    void scoresShrinkWithCorpusFrequencyAndDocLength() {
        double rare = PgBm25KeywordSearch.score(1, 10, 10.0, 100, 1);
        double common = PgBm25KeywordSearch.score(1, 10, 10.0, 100, 50);
        assertThat(rare).isGreaterThan(common);

        double longDoc = PgBm25KeywordSearch.score(1, 100, 10.0, 100, 10);
        assertThat(longDoc).isLessThan(common);
    }
}
