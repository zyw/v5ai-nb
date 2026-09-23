package xin.v5ai.nb.skill.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.skill.domain.Skill;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * Skill 视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = Skill.class)
public class SkillVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String description;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    /**
     * 当前生效的已发布版本 ID
     */
    private Long currentVersionId;

    /**
     * 当前生效版本号（由服务从 v5ai_skill_version 推导填充）
     */
    private Long currentVersion;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
