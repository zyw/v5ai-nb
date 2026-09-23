package xin.v5ai.nb.rag.core.tokenizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JiebaTextTokenizer} 单测：索引/查询两侧口径（重叠短词、去重）、停用词与标点过滤、大小写归一。
 *
 * @author ZYW
 * @since 2026-09-09
 */
class JiebaTextTokenizerTest {

    private final JiebaTextTokenizer tokenizer = new JiebaTextTokenizer();

    @Test
    void indexModeKeepsOverlappingShortWordsWhileSearchModeKeepsWholeWord() {
        // INDEX 切出可被查询命中的短词（含重叠），SEARCH 按检索粒度切词
        assertThat(tokenizer.tokenizeForIndex("中华人民共和国"))
                .contains("中华", "华人", "人民", "共和国", "中华人民共和国");
        assertThat(tokenizer.tokenizeForSearch("中华人民共和国")).containsExactly("中华人民共和国");
    }

    @Test
    void indexModeKeepsDuplicatesForTermFrequency() {
        // 词频 tf 由数组内出现次数现算 → 索引侧必须保留重复词
        assertThat(tokenizer.tokenizeForIndex("温度控制温度控制"))
                .containsExactly("温度", "控制", "温度控制", "温度", "控制", "温度控制");
    }

    @Test
    void searchModeDeduplicatesQueryTerms() {
        assertThat(tokenizer.tokenizeForSearch("温度控制 温度控制")).containsExactly("温度控制");
    }

    @Test
    void filtersStopWordsAndPunctuationTokens() {
        assertThat(tokenizer.tokenizeForSearch("如何提高 GPT-4 的推理能力？"))
                .contains("提高", "推理", "能力", "gpt", "4")
                .doesNotContain("的", "如何", "-", " ", "？");
    }

    @Test
    void lowercasesEnglishAndKeepsAlphanumericTokens() {
        assertThat(tokenizer.tokenizeForIndex("BM25 JIEBA v5ai")).contains("bm25", "jieba", "v5ai");
    }

    @Test
    void blankAndStopWordOnlyInputYieldsNoTerms() {
        assertThat(tokenizer.tokenizeForIndex(null)).isEmpty();
        assertThat(tokenizer.tokenizeForSearch("   ")).isEmpty();
        assertThat(tokenizer.tokenizeForSearch("的了的")).isEmpty();
    }
}
