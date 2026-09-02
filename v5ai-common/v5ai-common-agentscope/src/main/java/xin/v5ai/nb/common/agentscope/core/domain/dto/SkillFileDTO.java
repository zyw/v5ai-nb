//package xin.v5ai.nb.common.agentscope.core.domain.dto;
//
//import lombok.Getter;
//import lombok.Setter;
//import lombok.ToString;
//
//import java.io.Serial;
//import java.io.Serializable;
//import java.time.OffsetDateTime;
//
///**
// * <p>
// * Skill 包文件实体（v5ai_skill_file）：发布版本的原始文件（SKILL.md、prompts/、resources/ 等），
// * 运行时据此做 Workspace 注入。表无 updated_at 列，故不继承 BaseEntity。
// * </p>
// *
// * @author ZYW
// * @since 2026-08-22
// */
//@Getter
//@Setter
//@ToString
//public class SkillFileDTO implements Serializable {
//
//    @Serial
//    private static final long serialVersionUID = 1L;
//
//    private Long id;
//
//    /**
//     * 所属 Skill
//     */
//    private Long skillId;
//
//    /**
//     * 所属版本
//     */
//    private Long versionId;
//
//    /**
//     * 包内相对路径（如 "SKILL.md"、"prompts/guide.md"）
//     */
//    private String filePath;
//
//    /**
//     * 文本内容（UTF-8）
//     */
//    private String content;
//
//    private OffsetDateTime createdAt;
//}
