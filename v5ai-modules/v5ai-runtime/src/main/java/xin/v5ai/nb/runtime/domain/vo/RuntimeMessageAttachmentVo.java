package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.runtime.domain.RuntimeMessageAttachment;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 消息附件展示对象。
 */
@Data
@AutoMapper(target = RuntimeMessageAttachment.class)
public class RuntimeMessageAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    /**
     * 所属消息（v5ai_message.id）
     */
    private Long messageId;
    /**
     * 资源标识（plm_resource.id）
     */
    private Long resourceId;
    /**
     * 附件类型；当前只有 IMAGE
     */
    private String type;
    /**
     * 同一条消息内附件的顺序（从 0 起）
     */
    private Integer ordinal;
    private OffsetDateTime createdAt;
}
