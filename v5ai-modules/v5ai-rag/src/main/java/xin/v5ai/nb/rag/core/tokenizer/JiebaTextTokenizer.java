package xin.v5ai.nb.rag.core.tokenizer;

import cn.hutool.core.util.StrUtil;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * jieba 分词实现（{@code com.huaban:jieba-analysis}）。
 * <p>
 * 单例复用：{@link JiebaSegmenter} 线程安全且运行时禁改词典；首次加载词典约 1~2s，
 * 故由 {@link #warmup()} 在启动时预热，避免首句检索被词典加载拖慢。
 * <ul>
 *   <li>索引侧 {@link JiebaSegmenter.SegMode#INDEX}：切出可被查询命中的短词（如「中华人民共和国」→ 中华/人民/共和国）；</li>
 *   <li>查询侧 {@link JiebaSegmenter.SegMode#SEARCH}：按检索粒度切词并去重；</li>
 *   <li>两侧统一小写化 + 过滤停用词与纯标点词（jieba 无内置停用词，见 {@link #STOP_WORDS}）。</li>
 * </ul>
 *
 * @author ZYW
 * @since 2026-09-09
 */
@Slf4j
@Component
public class JiebaTextTokenizer implements TextTokenizer {

    /**
     * 内置轻量停用词表（中文高频虚词 + 基础英文停用词）；自定义词典本期不做。
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "是", "就", "都", "而", "及", "与", "着", "或", "一个", "没有", "我们", "你们",
            "他们", "它们", "这个", "那个", "这些", "那些", "这样", "那样", "什么", "怎么", "如何", "为什么",
            "可以", "因为", "所以", "但是", "如果", "并且", "以及", "还是", "也是", "不是", "在", "有", "我",
            "你", "他", "她", "它", "这", "那", "会", "能", "要", "对", "从", "到", "把", "被", "让", "给",
            "上", "下", "中", "里", "个", "们", "之", "其", "并", "以", "为", "于", "等", "很", "更", "最",
            "a", "an", "the", "and", "or", "but", "if", "then", "of", "to", "in", "on", "at", "by", "for",
            "with", "from", "as", "is", "are", "was", "were", "be", "been", "being", "do", "does", "did",
            "it", "its", "this", "that", "these", "those", "we", "you", "they", "he", "she", "not", "no");

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    /**
     * 启动预热：触发词典加载（无返回值，失败仅告警不阻断启动）。
     */
    @PostConstruct
    public void warmup() {
        long start = System.currentTimeMillis();
        try {
            segmenter.process("预热分词词典", JiebaSegmenter.SegMode.SEARCH);
            log.info("jieba 分词词典预热完成，耗时 {}ms", System.currentTimeMillis() - start);
        } catch (Exception exception) {
            log.warn("jieba 分词词典预热失败（首次检索将承担加载开销）: {}", exception.getMessage());
        }
    }

    @Override
    public List<String> tokenizeForIndex(String text) {
        return tokenize(text, JiebaSegmenter.SegMode.INDEX);
    }

    @Override
    public List<String> tokenizeForSearch(String text) {
        // 查询词项去重（保序）：同一词项在多处出现不应重复计分
        return List.copyOf(new LinkedHashSet<>(tokenize(text, JiebaSegmenter.SegMode.SEARCH)));
    }

    private List<String> tokenize(String text, JiebaSegmenter.SegMode mode) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        List<SegToken> tokens = segmenter.process(text, mode);
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<String>(tokens.size());
        for (SegToken token : tokens) {
            String word = token.word == null ? null : token.word.trim().toLowerCase(Locale.ROOT);
            if (isIndexable(word)) {
                result.add(word);
            }
        }
        return result;
    }

    /**
     * 词项可用性：非空、非停用词、且至少含一个字母或数字（纯标点/空白词项无检索意义）。
     */
    private static boolean isIndexable(String word) {
        if (word == null || word.isEmpty() || STOP_WORDS.contains(word)) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetterOrDigit(word.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
