package xin.v5ai.nb.rag.core.fusion;

import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.store.VectorStore;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「向量 + 关键词」两路命中的融合工具（调试检索与运行时注入共用，去重键与运行时合并一致：库+文档+片序号）。
 * <ul>
 *   <li>向量路得分 = 余弦相似度（0~1）；关键词路得分按存储实现（pgvector ILIKE 粗检恒 1.0 / ES 用 _score）；</li>
 *   <li>RRF 按两路名次计分 {@code sum(1/(rrfK + rank))}；仅一路有结果时直接返回该路（保留原始得分口径）；</li>
 *   <li>WEIGHTED_SUM 合并分 = {@code denseWeight×向量相似度 + (1-denseWeight)×关键词分}，缺失一路计 0；</li>
 *   <li>{@link FusionStrategy#VECTOR} / {@link FusionStrategy#KEYWORD} 仅返回对应单路。</li>
 * </ul>
 *
 * @author ZYW
 * @since 2026-09-19
 */
public final class RetrievalFusion {

    /** RRF K 值契约范围：调试端越界拒绝（{@link #requireRrfK}）；运行时对存量配置钳制兜底（{@link #clampRrfK}）。 */
    public static final int RRF_K_MIN = 1;
    public static final int RRF_K_MAX = 200;

    private RetrievalFusion() {
    }

    /**
     * 融合两路命中（不截断；调用方按自身条数预算截取）。
     *
     * @param strategy     融合策略（须已解析校验）
     * @param rrfK         RRF K 值（仅 RRF 生效）
     * @param denseWeight  向量路权重（仅 WEIGHTED_SUM 生效，关键词权重 = 1 - denseWeight）
     */
    public static List<VectorStore.RetrievalHit> fuse(List<VectorStore.RetrievalHit> vector,
                                                      List<VectorStore.RetrievalHit> keyword,
                                                      FusionStrategy strategy, int rrfK, double denseWeight) {
        if (strategy == FusionStrategy.VECTOR) {
            return vector;
        }
        if (strategy == FusionStrategy.KEYWORD) {
            return keyword;
        }
        if (strategy == FusionStrategy.WEIGHTED_SUM) {
            return weightedSum(vector, keyword, denseWeight);
        }
        // RRF：以两路名次融合
        if (vector.isEmpty()) {
            return keyword;
        }
        if (keyword.isEmpty()) {
            return vector;
        }
        Map<String, Ranked> fused = new HashMap<>();
        for (int i = 0; i < vector.size(); i++) {
            VectorStore.RetrievalHit hit = vector.get(i);
            fused.computeIfAbsent(key(hit), k -> new Ranked(hit)).add(i + 1, rrfK);
        }
        for (int i = 0; i < keyword.size(); i++) {
            VectorStore.RetrievalHit hit = keyword.get(i);
            fused.computeIfAbsent(key(hit), k -> new Ranked(hit)).add(i + 1, rrfK);
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble((Ranked r) -> r.score).reversed())
                .map(r -> new VectorStore.RetrievalHit(
                        r.hit.vectorId(), r.hit.knowledgeBaseId(), r.hit.documentId(), r.hit.chunkIndex(),
                        r.hit.content(), r.hit.metadata(), r.score))
                .toList();
    }

    /**
     * WEIGHTED_SUM 融合：合并分 = denseWeight×向量相似度 + (1-denseWeight)×关键词分；
     * 去重键与 RRF 一致（库+文档+片序号）；仅一路有结果时直接返回该路（保留原始得分口径）。
     */
    private static List<VectorStore.RetrievalHit> weightedSum(List<VectorStore.RetrievalHit> vector,
                                                              List<VectorStore.RetrievalHit> keyword,
                                                              double denseWeight) {
        if (vector.isEmpty()) {
            return keyword;
        }
        if (keyword.isEmpty()) {
            return vector;
        }
        Map<String, WeightedHit> acc = new HashMap<>();
        for (VectorStore.RetrievalHit hit : vector) {
            acc.computeIfAbsent(key(hit), k -> new WeightedHit(hit)).vectorScore = hit.score();
        }
        for (VectorStore.RetrievalHit hit : keyword) {
            acc.computeIfAbsent(key(hit), k -> new WeightedHit(hit)).keywordScore = hit.score();
        }
        return acc.values().stream()
                .map(w -> new VectorStore.RetrievalHit(
                        w.hit.vectorId(), w.hit.knowledgeBaseId(), w.hit.documentId(), w.hit.chunkIndex(),
                        w.hit.content(), w.hit.metadata(),
                        denseWeight * w.vectorScore + (1 - denseWeight) * w.keywordScore))
                .sorted(Comparator.comparingDouble((VectorStore.RetrievalHit h) -> h.score()).reversed())
                .toList();
    }

    /**
     * 阈值过滤（仅向量路得分参与：关键词粗检语义无阈值意义）。
     */
    public static List<VectorStore.RetrievalHit> filterByThreshold(
            List<VectorStore.RetrievalHit> hits, double threshold) {
        return hits.stream()
                .filter(hit -> hit.score() >= threshold)
                .toList();
    }

    /** RRF K 值越界即拒绝（调试/契约口径：1~200）。 */
    public static int requireRrfK(int rrfK) {
        if (rrfK < RRF_K_MIN || rrfK > RRF_K_MAX) {
            throw new ServiceException("RRF K 值须在 " + RRF_K_MIN + "~" + RRF_K_MAX + " 之间: " + rrfK);
        }
        return rrfK;
    }

    /** RRF K 值越界钳制（运行时读取存量配置的兜底口径，避免坏数据导致除零/异常排名）。 */
    public static int clampRrfK(int rrfK) {
        return clamp(rrfK, RRF_K_MIN, RRF_K_MAX);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clampWeight(double weight) {
        return Math.max(0.0, Math.min(1.0, weight));
    }

    private static String key(VectorStore.RetrievalHit hit) {
        return hit.knowledgeBaseId() + ":" + hit.documentId() + ":" + hit.chunkIndex();
    }

    /** WEIGHTED_SUM 累计项：记录两路得分（缺失一路计 0）。 */
    private static final class WeightedHit {
        private final VectorStore.RetrievalHit hit;
        private double vectorScore;
        private double keywordScore;

        private WeightedHit(VectorStore.RetrievalHit hit) {
            this.hit = hit;
        }
    }

    private static final class Ranked {
        private final VectorStore.RetrievalHit hit;
        private double score;

        private Ranked(VectorStore.RetrievalHit hit) {
            this.hit = hit;
        }

        private void add(int rank, int rrfK) {
            score += 1.0 / (rrfK + rank);
        }
    }
}
