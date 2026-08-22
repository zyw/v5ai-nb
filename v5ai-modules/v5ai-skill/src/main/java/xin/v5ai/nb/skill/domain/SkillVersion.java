package xin.v5ai.nb.skill.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * <p>
 * Skill 版本实体（v5ai_skill_version）：上传包生成 DRAFT，发布后不可变。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_skill_version")
public class SkillVersion extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Skill
     */
    private Long skillId;

    /**
     * 版本号（每个 Skill 内自增）
     */
    private Long version;

    /**
     * 状态（DRAFT / PUBLISHED）
     */
    private String status;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 发布时间（未发布为 null）
     */
    private LocalDateTime publishedAt;
}
