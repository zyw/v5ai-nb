package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_message")
public class RuntimeMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键：MyBatis-Plus 的 ASSIGN_ID 雪花号（19 位，{@code v5ai_message.id} 虽然声明为
     * {@code BIGSERIAL}，实际值由 MP 生成、不走库里的序列）。超出 JS 的
     * {@code Number.MAX_SAFE_INTEGER}，因此对外（历史接口、{@code RUN_STARTED} 载荷）
     * 一律**以字符串给出**，回传也按字符串收——否则会被浏览器四舍五入成另一个 id。
     */
    @TableId
    private Long id;
    /**
     * 会话ID
     */
    private String conversationId;
    /**
     * agent key
     */
    private String agentKey;
    /**
     * 角色role USER or ASSISTANT
     */
    private String role;
    /**
     * 内容
     */
    private String content;
    /**
     * 模型的思考过程（仅助手消息有值；NULL=没有思考或 V42 之前的历史数据）。
     *
     * <p>供门户历史回看，**不回放给模型**：记忆窗口与摘要的取数会显式剔除这一列
     * （见 {@code RuntimeMessageServiceImpl}），因为它可能很长而每轮都要读。</p>
     */
    private String reasoning;
    /**
     * 消息扩展元数据（JSON 文本）：当前唯一语义是**引用快照**
     * （{@code {"citations":[…]}}，与 {@code RETRIEVAL} 事件载荷同构）。
     *
     * <p>供门户历史回看，**不回放给模型**：与 {@code reasoning} 一样，记忆窗口与摘要的取数
     * 会显式剔除这一列（见 {@code RuntimeMessageServiceImpl.HOT_PATH_EXCLUDED_COLUMNS}），
     * 否则每轮都会多读每条消息约 8.5KB。NULL = 这一轮没有引用（未启用 RAG / 无命中 /
     * V45 之前的历史数据）。见 docs/adr/0009-citations-persisted-on-assistant-message.md。</p>
     */
    private String metadata;
    /**
     * 该回答的输入 token：模型回报的真实值优先，未回报时为平台估算（仅助手消息有值）
     */
    private Integer promptTokens;
    /**
     * 该回答的输出 token：口径同上（仅助手消息有值）
     */
    private Integer completionTokens;
    /**
     * 该次运行的服务端耗时（毫秒，仅助手消息有值）
     */
    private Integer durationMs;
    /**
     * 作废时间；非空表示该消息已被「重新生成」替换（不参与展示与记忆回放，数据保留）
     */
    private OffsetDateTime supersededAt;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
