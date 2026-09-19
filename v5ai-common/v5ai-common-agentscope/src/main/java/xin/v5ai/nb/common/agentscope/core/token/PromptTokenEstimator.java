package xin.v5ai.nb.common.agentscope.core.token;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;

/**
 * 平台侧 token 估算器：模型没有回报用量时的兜底口径。
 *
 * <p>模型回报的真实用量永远优先（见 {@link xin.v5ai.nb.common.agentscope.core.AgentTextEvent.Usage}）。
 * 这里只在这两种情况下被用到：上游执行器没有发出真实用量，或本次运行在模型回报用量之前就失败 /
 * 被取消。</p>
 *
 * <p>口径刻意区别于「4 字符 1 token」的粗糙经验值：中日韩文字与全角符号按 <b>1 字 1 token</b> 计
 * （主流分词器对 CJK 的压缩率接近 1:1，用 4 字符 1 token 会把中文长会话低估 3-4 倍），其余字符
 * 仍按每 4 个 1 token 向上取整。图片按张计固定值——{@code AttachmentRef} 只有资源引用、没有宽高，
 * 做不了瓦片估算。</p>
 */
public final class PromptTokenEstimator {

    /** 非 CJK 字符的折算比例：约 4 个字符 1 token。 */
    private static final int NON_CJK_CHARS_PER_TOKEN = 4;

    /** 每张图片在估算里折算的 token 数：{@code AttachmentRef} 没有宽高，只按张计。 */
    public static final long DEFAULT_IMAGE_TOKENS = 1024;

    private PromptTokenEstimator() {
    }

    /**
     * 估算一段文本的 token 数。
     *
     * @param text 文本；为 {@code null} 或空串时返回 0
     * @return 估算 token 数
     */
    public static long estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long cjk = 0;
        long other = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (isCjkLike(codePoint)) {
                cjk++;
            } else {
                other++;
            }
            i += Character.charCount(codePoint);
        }
        return cjk + (other + NON_CJK_CHARS_PER_TOKEN - 1) / NON_CJK_CHARS_PER_TOKEN;
    }

    /**
     * 估算一份「即将发给模型的消息列表」的输入 token：文本块按字符估算，其余块（图片）按张计。
     *
     * @param messages           消息列表；可为 {@code null}
     * @param imageTokensPerImage 每张图片折算的 token 数；{@code <= 0} 表示不计图片
     * @return 估算 token 数
     */
    public static long estimateMessages(java.util.List<Msg> messages, long imageTokensPerImage) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long tokens = 0;
        for (Msg message : messages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            for (ContentBlock block : message.getContent()) {
                if (block instanceof TextBlock textBlock) {
                    tokens += estimateText(textBlock.getText());
                } else if (block != null && imageTokensPerImage > 0) {
                    // 目前只会出现图片块（文本块之外的一切都按图片计），没有尺寸信息可用
                    tokens += imageTokensPerImage;
                }
            }
        }
        return tokens;
    }

    /**
     * 按请求字段估算输入 token：本轮的提问、RAG 上下文、历史正文与两处图片（当前提问 + 历史）。
     *
     * <p>不含系统提示词——调用方（{@link AgentRunBo}）拿不到它的原文；真实用量优先时本方法也不会
     * 被调用。</p>
     *
     * @param request             本次运行请求
     * @param imageTokensPerImage 每张图片折算的 token 数
     * @return 估算 token 数
     */
    public static long estimateRequest(AgentRunBo request, long imageTokensPerImage) {
        if (request == null) {
            return 0;
        }
        long tokens = estimateText(request.query()) + estimateText(request.ragContext());
        for (SessionMessage history : request.history()) {
            tokens += estimateText(history.content());
            if (imageTokensPerImage > 0) {
                tokens += (long) history.attachments().size() * imageTokensPerImage;
            }
        }
        if (imageTokensPerImage > 0) {
            tokens += (long) request.attachments().size() * imageTokensPerImage;
        }
        return tokens;
    }

    /**
     * 是否按「1 字 1 token」计的字符：CJK 汉字（含扩展区）、假名、谚文、全角符号与 CJK 标点。
     */
    private static boolean isCjkLike(int codePoint) {
        return (codePoint >= 0x3000 && codePoint <= 0x303F)      // CJK 标点
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)  // 假名
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)  // 汉字扩展 A
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)  // 汉字基本区
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)  // 谚文音节
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)  // 兼容汉字
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF)  // 全角字符
                || (codePoint >= 0x20000 && codePoint <= 0x2FA1F); // 汉字扩展 B 及以后
    }
}
