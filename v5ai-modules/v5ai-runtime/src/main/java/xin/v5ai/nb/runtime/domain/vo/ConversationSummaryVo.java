package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.ConversationSummary;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = ConversationSummary.class)
public class ConversationSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String conversationId;

    private String summary;

    private Long coveredUntilMessageId;

    private Integer coveredMessages;

    private Long modelId;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
