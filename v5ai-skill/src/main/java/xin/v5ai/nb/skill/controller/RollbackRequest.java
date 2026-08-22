package xin.v5ai.nb.skill.controller;

/**
 * Skill 回滚请求体。
 *
 * @param versionId 要回滚到的已发布版本 ID
 */
public record RollbackRequest(Long versionId) {
}
