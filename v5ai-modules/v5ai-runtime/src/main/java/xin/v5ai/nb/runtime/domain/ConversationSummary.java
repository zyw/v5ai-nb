package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 会话历史摘要：同会话历史窗口之外的旧内容压缩成一段文本（一会话一行，滚动覆盖）。
 *
 * <p>{@code coveredUntilMessageId} 是覆盖水位——{@code id <= 水位} 的消息都已并入摘要。
 * 写入侧只在「新水位更高」时覆盖（见 {@code ConversationSummaryMapper.upsertIfNewer}）。</p>
 */
@Data
@TableName("v5ai_conversation_summary")
public class ConversationSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "conversation_id")
    private String conversationId;

    private String summary;

    private Long coveredUntilMessageId;

    private Integer coveredMessages;

    private Long modelId;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
