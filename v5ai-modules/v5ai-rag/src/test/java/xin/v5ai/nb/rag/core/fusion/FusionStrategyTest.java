package xin.v5ai.nb.rag.core.fusion;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FusionStrategy} 解析与取值校验。
 *
 * @author ZYW
 * @since 2026-09-19
 */
class FusionStrategyTest {

    @Test
    void parse_isCaseInsensitiveAndTrims() {
        assertThat(FusionStrategy.parse("rrf")).isEqualTo(FusionStrategy.RRF);
        assertThat(FusionStrategy.parse("  Weighted_Sum ")).isEqualTo(FusionStrategy.WEIGHTED_SUM);
        assertThat(FusionStrategy.parse("vector")).isEqualTo(FusionStrategy.VECTOR);
        assertThat(FusionStrategy.parse("KEYWORD")).isEqualTo(FusionStrategy.KEYWORD);
    }

    @Test
    void parse_blankFallsBackToDefaultRrf() {
        assertThat(FusionStrategy.parse(null)).isEqualTo(FusionStrategy.RRF);
        assertThat(FusionStrategy.parse("  ")).isEqualTo(FusionStrategy.RRF);
        assertThat(FusionStrategy.DEFAULT).isEqualTo(FusionStrategy.RRF);
    }

    @Test
    void parse_unknownValueIsRejected() {
        assertThatThrownBy(() -> FusionStrategy.parse("MIX"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的融合策略");
    }
}
