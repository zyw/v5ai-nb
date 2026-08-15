package xin.v5ai.nb.infrastructure.skill;

import java.util.List;

public interface SkillFileGateway {
    void insertFile(SkillFileEntity entity);

    List<SkillFileEntity> selectFilesByVersion(Long versionId);

    void deleteFilesByVersion(Long versionId);
}
