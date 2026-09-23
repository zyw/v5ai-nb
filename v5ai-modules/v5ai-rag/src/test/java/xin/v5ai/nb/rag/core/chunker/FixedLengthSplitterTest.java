package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FixedLengthSplitter} 单测：固定窗口滑动切分（带重叠）与空白归一。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class FixedLengthSplitterTest {

    private final FixedLengthSplitter splitter = new FixedLengthSplitter();

    @Test
    void windowsWithOverlap() {
        assertThat(splitter.split("abcdefghijklmnopqrst", 10, 2))
                .containsExactly("abcdefghij", "ijklmnopqr", "qrst");
    }

    @Test
    void normalizesWhitespace() {
        assertThat(splitter.split("a\n\n b \n", 100, 0)).containsExactly("a b");
    }

    @Test
    void shortTextStaysWhole() {
        assertThat(splitter.split("hello", 10, 0)).containsExactly("hello");
    }
}
