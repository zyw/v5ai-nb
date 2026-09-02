package xin.v5ai.nb.skill.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.skill.domain.SkillFile;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Skill 文件视图对象（在线编辑器使用，携带完整内容）。
 */
@Data
@AutoMapper(target = SkillFile.class)
public class SkillFileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 所属 Skill
     */
    private Long skillId;

    /**
     * 所属版本
     */
    private Long versionId;

    /**
     * 包内相对路径（如 "SKILL.md"、"prompts/guide.md"）
     */
    private String filePath;

    /**
     * 文本内容（UTF-8）
     */
    private String content;

    private OffsetDateTime createdAt;
}
