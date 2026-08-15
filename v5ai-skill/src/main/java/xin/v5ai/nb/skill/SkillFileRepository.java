package xin.v5ai.nb.skill;

import java.util.List;
import java.util.Map;

public interface SkillFileRepository {
    /** 保存某版本的全部文件（调用方保证路径唯一）。 */
    void saveFiles(Long skillId, Long versionId, Map<String, String> files);

    List<SkillFile> findByVersionId(Long versionId);

    void deleteByVersionId(Long versionId);
}
