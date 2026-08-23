package xin.v5ai.nb.skill.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.skill.domain.SkillVersion;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * Skill 版本视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = SkillVersion.class)
public class SkillVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long skillId;

    /**
     * 版本号
     */
    private Long version;

    /**
     * 状态（DRAFT / PUBLISHED）
     */
    private String status;

    private String description;

    /**
     * 发布时间（未发布为 null）
     */
    private OffsetDateTime publishedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
