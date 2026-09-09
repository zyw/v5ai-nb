package xin.v5ai.nb.rag.core.fusion;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.store.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RetrievalFusion} 纯逻辑单测：四策略融合、去重、单路为空回退、rrfK 范围。
 *
 * @author ZYW
 * @since 2026-09-19
 */
class RetrievalFusionTest {

    private static VectorStore.RetrievalHit hit(Long docId, int index, String content, double score) {
        return new VectorStore.RetrievalHit("v" + docId + "-" + index, 1L, docId, index, content, "{}", score);
    }

    @Test
    void fuse_rrfRanksAcrossChannelsByReciprocalRank() {
        // 向量路：A(rank1) B(rank2)；关键词路：B(rank1) C(rank2)；k=60
        // A = 1/61；B = 1/62 + 1/61；C = 1/62 → 顺序 B, A, C
        List<VectorStore.RetrievalHit> vector = List.of(
                hit(1L, 0, "vec-A", 0.9),
                hit(2L, 0, "vec-B", 0.8));
        List<VectorStore.RetrievalHit> keyword = List.of(
                hit(2L, 0, "kw-B", 1.0),
                hit(3L, 0, "kw-C", 1.0));

        List<VectorStore.RetrievalHit> fused =
                RetrievalFusion.fuse(vector, keyword, FusionStrategy.RRF, 60, 0.5);

        assertThat(fused).extracting(VectorStore.RetrievalHit::documentId)
                .containsExactly(2L, 1L, 3L);
        // 同片（doc2）在 RRF 下只保留一条且得分 = 1/61 + 1/62
        assertThat(fused.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get(1).score()).isCloseTo(1.0 / 61, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get(2).score()).isCloseTo(1.0 / 62, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void fuse_vectorAndKeywordOnlyReturnSingleChannel() {
        List<VectorStore.RetrievalHit> vector = List.of(hit(1L, 0, "vec-A", 0.9));
        List<VectorStore.RetrievalHit> keyword = List.of(hit(2L, 0, "kw-B", 1.0));

        List<VectorStore.RetrievalHit> vectorOnly =
                RetrievalFusion.fuse(vector, keyword, FusionStrategy.VECTOR, 60, 0.5);
        assertThat(vectorOnly).extracting(VectorStore.RetrievalHit::documentId).containsExactly(1L);
        assertThat(vectorOnly.get(0).score()).isEqualTo(0.9);

        List<VectorStore.RetrievalHit> keywordOnly =
                RetrievalFusion.fuse(vector, keyword, FusionStrategy.KEYWORD, 60, 0.5);
        assertThat(keywordOnly).extracting(VectorStore.RetrievalHit::documentId).containsExactly(2L);
        assertThat(keywordOnly.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void fuse_weightedSumCombinesScoresWithMissingChannelZero() {
        List<VectorStore.RetrievalHit> vector = List.of(
                hit(1L, 0, "仅向量A", 0.9),
                hit(2L, 0, "两路B", 0.8));
        List<VectorStore.RetrievalHit> keyword = List.of(
                hit(2L, 0, "两路B", 1.0),
                hit(3L, 0, "仅关键词C", 1.0));

        List<VectorStore.RetrievalHit> fused =
                RetrievalFusion.fuse(vector, keyword, FusionStrategy.WEIGHTED_SUM, 60, 0.5);

        // B = 0.5×0.8 + 0.5×1.0 = 0.9；C = 0.5×0 + 0.5×1.0 = 0.5；A = 0.5×0.9 + 0.5×0 = 0.45
        assertThat(fused).extracting(VectorStore.RetrievalHit::documentId)
                .containsExactly(2L, 3L, 1L);
        assertThat(fused.get(0).score()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get(1).score()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get(2).score()).isCloseTo(0.45, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void fuse_singlePathEmptyReturnsTheOtherPath() {
        List<VectorStore.RetrievalHit> vector = List.of(hit(1L, 0, "vec-A", 0.9));
        List<VectorStore.RetrievalHit> keyword = List.of(hit(2L, 0, "kw-B", 1.0));

        assertThat(RetrievalFusion.fuse(List.of(), keyword, FusionStrategy.RRF, 60, 0.5))
                .extracting(VectorStore.RetrievalHit::documentId).containsExactly(2L);
        assertThat(RetrievalFusion.fuse(vector, List.of(), FusionStrategy.WEIGHTED_SUM, 60, 0.5))
                .extracting(VectorStore.RetrievalHit::documentId).containsExactly(1L);
        assertThat(RetrievalFusion.fuse(List.of(), List.of(), FusionStrategy.RRF, 60, 0.5)).isEmpty();
    }

    @Test
    void requireRrfKRejectsOutOfRangeAndClampCaps() {
        assertThat(RetrievalFusion.requireRrfK(1)).isEqualTo(1);
        assertThat(RetrievalFusion.requireRrfK(200)).isEqualTo(200);
        assertThatThrownBy(() -> RetrievalFusion.requireRrfK(0))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("1~200");
        assertThatThrownBy(() -> RetrievalFusion.requireRrfK(201))
                .isInstanceOf(ServiceException.class);

        assertThat(RetrievalFusion.clampRrfK(0)).isEqualTo(1);
        assertThat(RetrievalFusion.clampRrfK(500)).isEqualTo(200);
        assertThat(RetrievalFusion.clampWeight(-0.5)).isEqualTo(0.0);
        assertThat(RetrievalFusion.clampWeight(1.5)).isEqualTo(1.0);
    }

    @Test
    void filterByThresholdKeepsOnlyScoreAbove() {
        List<VectorStore.RetrievalHit> hits = List.of(
                hit(1L, 0, "高", 0.9), hit(2L, 0, "低", 0.5));
        assertThat(RetrievalFusion.filterByThreshold(hits, 0.6))
                .extracting(VectorStore.RetrievalHit::documentId).containsExactly(1L);
    }
}
