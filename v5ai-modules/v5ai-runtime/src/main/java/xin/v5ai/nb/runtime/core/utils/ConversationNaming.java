package xin.v5ai.nb.runtime.core.utils;

import java.util.regex.Pattern;

/**
 * 会话名称的生成规则（纯函数，无 Spring 依赖）。
 *
 * <p>名称在**写入侧**生成并落库（{@code v5ai_conversation.name}），会话列表因此只读会话表，
 * 不必再对每个会话 {@code DISTINCT ON} 查一次 {@code v5ai_message} 取首条提问做兜底
 * （消息表一大，列表就成了最贵的接口）。</p>
 *
 * <p>两级生成：{@link #fromQuery} 是**兜底**（会话创建瞬间、零成本、保证 name 非空）；
 * {@link #cleanModelTitle} 清洗模型返回的标题，作为首轮结束后的可选覆盖。
 * 两者都受 {@link #MAX_NAME_LENGTH} 约束，与 {@code v5ai_conversation.name VARCHAR(100)} 一致。</p>
 */
public final class ConversationNaming {

    /**
     * 会话名称长度上限：与 {@code v5ai_conversation.name VARCHAR(100)}、
     * 门户改名接口的校验（{@code ChatController}）保持同一处定义。
     */
    public static final int MAX_NAME_LENGTH = 100;

    /**
     * 名称来源 - 系统生成：首条提问的兜底名，或首轮结束后模型改写的标题；可被模型覆盖。
     */
    public static final String SOURCE_AUTO = "AUTO";

    /**
     * 名称来源 - 用户改名：用户在门户改过的名字，任何自动命名都不得覆盖。
     */
    public static final String SOURCE_USER = "USER";

    /**
     * 送给标题模型的提问长度上限：标题只要首句语义，长文提问没必要整段付费。
     */
    private static final int MAX_QUESTION_LENGTH = 500;

    private static final String ELLIPSIS = "…";

    /** 连续空白（含换行、制表）压成单个空格：名称要能在一行里显示。 */
    private static final Pattern BLANK = Pattern.compile("\\s+");

    /** 模型偶尔会带前缀说明，去掉它只留标题本身。 */
    private static final Pattern TITLE_PREFIX =
            Pattern.compile("^(标题|会话名|会话名称|名称|title)\\s*[:：]\\s*", Pattern.CASE_INSENSITIVE);

    /** 标题不需要句末标点：模型常把提问原样抄回来。 */
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[。．.！!？?，,；;：:]+$");

    /** 包裹标题的成对符号（中英文引号、书名号、括号）。 */
    private static final String WRAPPERS = "\"'“”‘’《》「」『』（）()[]【】";

    private ConversationNaming() {
    }

    /**
     * 首条提问 → 兜底会话名：取首行、连续空白压成单空格、去首尾空白、按码点截到 100。
     *
     * @param query 用户提问
     * @return 名称；提问为空或首行无内容时返回 {@code null}（该会话保持未命名，由前端显示占位）
     */
    public static String fromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        int lineEnd = query.indexOf('\n');
        String firstLine = lineEnd < 0 ? query : query.substring(0, lineEnd);
        String collapsed = collapse(firstLine);
        return collapsed.isEmpty() ? null : truncate(collapsed, MAX_NAME_LENGTH);
    }

    /**
     * 模型返回的标题 → 可落库的名称：压缩空白、去掉「标题：」前缀与包裹符号、去掉句末标点，再截断。
     *
     * @param raw       模型原始输出
     * @param maxLength 标题长度上限（配置项，通常远小于 {@link #MAX_NAME_LENGTH}）
     * @return 名称；输出为空或清洗后没剩下内容时返回 {@code null}（调用方保留兜底名）
     */
    public static String cleanModelTitle(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = TITLE_PREFIX.matcher(collapse(raw)).replaceFirst("");
        cleaned = stripWrappers(cleaned.trim());
        cleaned = TRAILING_PUNCTUATION.matcher(cleaned).replaceFirst("").trim();
        cleaned = stripWrappers(cleaned);
        if (cleaned.isEmpty()) {
            return null;
        }
        // 模型标题比兜底名短，但仍要挡住「模型不听话输出一整段」的极端情况
        return truncate(cleaned, Math.min(maxLength, MAX_NAME_LENGTH));
    }

    /**
     * 提问 → 送给标题模型的输入：压缩空白并截断，避免长文提问整段进 prompt。
     *
     * @param query 用户提问
     * @return 可直接进 prompt 的文本；提问为空时返回 {@code null}（没有可命名的内容）
     */
    public static String questionForTitle(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String collapsed = collapse(query);
        return collapsed.isEmpty() ? null : truncate(collapsed, MAX_QUESTION_LENGTH);
    }

    private static String collapse(String text) {
        return BLANK.matcher(text).replaceAll(" ").trim();
    }

    /**
     * 按**码点**截断并补省略号：保证结果不超过 {@code maxLength} 个码点，
     * 且不会把 emoji 的代理对切成半个字符（{@code String.length()} 会数成两个）。
     */
    private static String truncate(String text, int maxLength) {
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        int end = text.offsetByCodePoints(0, Math.max(1, maxLength - 1));
        return text.substring(0, end) + ELLIPSIS;
    }

    /** 去掉首尾成对的引号/书名号/括号（模型常把标题包在引号里）。 */
    private static String stripWrappers(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && WRAPPERS.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && WRAPPERS.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end).trim();
    }
}
