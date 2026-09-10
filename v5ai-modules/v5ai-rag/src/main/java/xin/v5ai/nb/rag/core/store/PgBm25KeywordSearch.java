package xin.v5ai.nb.rag.core.store;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.store.VectorStore.RetrievalHit;
import xin.v5ai.nb.rag.core.tokenizer.TextTokenizer;
import xin.v5ai.nb.rag.domain.KnowledgeChunkDO;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PG 原生关键词路的 Okapi BM25 计分（替代原 {@code ILIKE} 粗检的恒 1.0 分）。
 * <p>
 * 流程：查询 jieba SEARCH 分词 → GIN 索引取候选行（{@code keyword_tokens && 词项}）→
 * Java 侧按 BM25 计分排序取前 topK。词频 tf / 文档长度 dl 由候选行的分词数组现算，
 * 语料规模 N / 平均长度 avgdl / 文档频率 df 按「查询涉及的知识库集合」查询时现算
 * （不做写时计数器；超大库再引入统计缓存）。
 * <p>
 * 公式：{@code score = Σ idf(t) × tf×(k1+1) / (tf + k1×(1-b+b×dl/avgdl))}，
 * {@code idf = ln(1 + (N - df + 0.5)/(df + 0.5))}，k1=1.2、b=0.75。
 * <p>
 * ES / 外部搜索引擎路与 Milvus 不涉及（沿用各自原实现）。
 *
 * @author ZYW
 * @since 2026-09-09
 */
@Component
@RequiredArgsConstructor
public class PgBm25KeywordSearch {

    /**
     * BM25 词频饱和参数
     */
    static final double K1 = 1.2;

    /**
     * BM25 文档长度归一参数
     */
    static final double B = 0.75;

    /**
     * 候选池倍数：候选需多于返回条数，否则截断先于计分发生会丢召回
     */
    private static final int CANDIDATE_FACTOR = 5;

    /**
     * 候选池下限
     */
    private static final int MIN_CANDIDATES = 200;

    private final KnowledgeChunkMapper chunkMapper;
    private final TextTokenizer textTokenizer;

    /**
     * BM25 关键词检索。
     *
     * @param knowledgeBaseIds 限定的知识库 ID 集合
     * @param keyword          查询文本
     * @param topK             返回条数
     * @return 命中列表（按 BM25 分降序）；{@link Optional#empty()} 表示查询文本无有效词项
     *         （全为停用词/标点），调用方回退原 ILIKE 粗检行为
     */
    public Optional<List<RetrievalHit>> search(List<Long> knowledgeBaseIds, String keyword, int topK) {
        List<String> terms = textTokenizer.tokenizeForSearch(keyword);
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        if (CollUtil.isEmpty(knowledgeBaseIds) || topK <= 0) {
            return Optional.of(List.of());
        }
        String[] termArray = terms.toArray(String[]::new);
        List<KnowledgeChunkDO> candidates =
                chunkMapper.findChunksByKeywordTokens(knowledgeBaseIds, termArray, candidateLimit(topK));
        if (CollUtil.isEmpty(candidates)) {
            return Optional.of(List.of());
        }
        long docCount = chunkMapper.countIndexedChunks(knowledgeBaseIds);
        double avgDocLength = chunkMapper.avgKeywordTokenLength(knowledgeBaseIds);
        Map<String, Long> docFreqs = new HashMap<>();
        for (String term : terms) {
            docFreqs.put(term, chunkMapper.countChunksByKeywordToken(knowledgeBaseIds, term));
        }
        // 语料统计缺失（存量未重建等）时退化为「候选集即语料」，保证不除零、不产生 NaN
        long corpusSize = docCount > 0 ? docCount : candidates.size();
        double averageLength = avgDocLength > 0 ? avgDocLength : 1.0;

        var scored = new ArrayList<ScoredHit>();
        for (KnowledgeChunkDO row : candidates) {
            double score = scoreRow(row, terms, docFreqs, corpusSize, averageLength);
            if (score > 0) {
                scored.add(new ScoredHit(row, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredHit::score).reversed()
                .thenComparing(hit -> hit.row().getDocumentId())
                .thenComparing(hit -> hit.row().getChunkIndex()));
        return Optional.of(scored.stream().limit(topK).map(PgBm25KeywordSearch::toHit).toList());
    }

    /**
     * 单行 BM25 计分：对查询词项求和；未命中词项（tf=0）不贡献。
     */
    private static double scoreRow(KnowledgeChunkDO row, List<String> terms, Map<String, Long> docFreqs,
                                   long docCount, double avgDocLength) {
        String[] tokens = row.getKeywordTokens();
        if (tokens == null || tokens.length == 0) {
            return 0;
        }
        double total = 0;
        for (String term : terms) {
            int termFreq = occurrences(tokens, term);
            if (termFreq == 0) {
                continue;
            }
            total += score(termFreq, tokens.length, avgDocLength, docCount, docFreqs.getOrDefault(term, 0L));
        }
        return total;
    }

    /**
     * Okapi BM25 单（词项, 文档）得分。
     *
     * @param termFreq     词项在文档内出现次数
     * @param docLength    文档长度（分词数组元素个数）
     * @param avgDocLength 语料平均文档长度（&gt; 0）
     * @param docCount     语料文档数 N
     * @param docFreq      包含该词项的文档数 df（&gt; 0）
     */
    static double score(int termFreq, int docLength, double avgDocLength, long docCount, long docFreq) {
        double idf = Math.log(1 + (docCount - docFreq + 0.5) / (docFreq + 0.5));
        double normalizer = K1 * (1 - B + B * docLength / avgDocLength);
        return idf * (termFreq * (K1 + 1)) / (termFreq + normalizer);
    }

    private static int occurrences(String[] tokens, String term) {
        int count = 0;
        for (String token : tokens) {
            if (term.equals(token)) {
                count++;
            }
        }
        return count;
    }

    private static int candidateLimit(int topK) {
        return Math.max(topK * CANDIDATE_FACTOR, MIN_CANDIDATES);
    }

    private static RetrievalHit toHit(ScoredHit scored) {
        KnowledgeChunkDO row = scored.row();
        return new RetrievalHit(row.getVectorId(), row.getKnowledgeBaseId(), row.getDocumentId(),
                row.getChunkIndex(), row.getContent(), StrUtil.toString(row.getMetadata()), scored.score());
    }

    private record ScoredHit(KnowledgeChunkDO row, double score) {
    }
}
