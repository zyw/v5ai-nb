package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 消息附件（{@code v5ai_message_attachment}）：一条消息携带的图片，
 * 以 {@code resource_id} 指向通用资源存储中的 ATTACHMENT 资源。
 *
 * <p>附件属于消息，不属于知识库；只有用户消息会带附件。</p>
 */
@Data
@TableName("v5ai_message_attachment")
public class RuntimeMessageAttachment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属消息（v5ai_message.id）
     */
    private Long messageId;

    /**
     * 资源标识（plm_resource.id，biz_type=ATTACHMENT）
     */
    private Long resourceId;

    /**
     * 附件类型；当前只有 IMAGE
     */
    private String type;

    /**
     * 同一条消息内附件的顺序（从 0 起），保证回放时模型看到的顺序与用户提交顺序一致
     */
    private Integer ordinal;

    private OffsetDateTime createdAt;
}
