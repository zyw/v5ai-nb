package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话摘要（{@code v5ai.chat.conversation-summary}）：把历史窗口之外的旧内容压缩成一段文本。
 *
 * <p>与标题改写（{@code v5ai.chat.conversation-title}）同一性质：异步、失败静默、不记用量——
 * 摘要只是为了「不丢信息」，生成失败不该影响对话主链路（旧摘要仍在，窗口内的内容照常回放）。</p>
 *
 * <p><b>用哪个模型不在这里配</b>：原先的 {@code v5ai.chat.conversation-summary.model-id} 已下线，
 * 改由 Agent 的<em>次要模型</em>决定（{@code v5ai_agent.secondary_model_id}，未配置则回退该 Agent
 * 绑定的对话模型）。这样「摘要用哪个模型」与 Agent 靠在一起，且与标题改写共用同一个字段，
 * 见 {@code xin.v5ai.nb.runtime.core.utils.AuxiliaryModel}。</p>
 */
@Data
@ConfigurationProperties(prefix = "v5ai.chat.conversation-summary")
public class ConversationSummaryProperties {

    /**
     * 是否启用摘要。关闭后历史窗口之外的内容直接丢弃（超窗不再顶爆，但模型会忘记更早的内容）。
     */
    private boolean enabled = true;

    /**
     * 单次摘要生成的超时（毫秒）：超时即放弃，保留旧摘要。
     */
    private long timeoutMillis = 20_000;

    /**
     * 触发阈值：水位之后至少新增多少条「窗口外」消息才值得再压一次（防抖、防空跑）。
     */
    private int minNewMessages = 10;

    /**
     * 一次最多把多少条窗口外消息喂给摘要模型（取最靠近窗口起点的那些）。
     */
    private int maxInputMessages = 100;

    /**
     * 单条输入消息的截断长度（字符）：长回答不必整段进摘要提示词。
     */
    private int maxInputCharsPerMessage = 500;

    /**
     * 摘要长度上限（字符）：超出直接硬截断，保证注入系统提示的总长度可控。
     */
    private int maxSummaryChars = 1_000;

    /**
     * 距上一次摘要至少间隔多少秒才允许再生成（同一会话连续追问时的防抖）。
     */
    private int minIntervalSeconds = 30;
}
