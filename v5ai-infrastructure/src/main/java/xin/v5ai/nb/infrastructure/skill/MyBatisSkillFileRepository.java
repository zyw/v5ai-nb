package xin.v5ai.nb.infrastructure.skill;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.skill.SkillFile;
import xin.v5ai.nb.skill.SkillFileRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class MyBatisSkillFileRepository implements SkillFileRepository {
    private final SkillFileGateway gateway;

    public MyBatisSkillFileRepository(SkillFileGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void saveFiles(Long skillId, Long versionId, Map<String, String> files) {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            var entity = new SkillFileEntity();
            entity.setSkillId(skillId);
            entity.setVersionId(versionId);
            entity.setFilePath(entry.getKey());
            entity.setContent(entry.getValue());
            entity.setCreatedAt(Instant.now());
            gateway.insertFile(entity);
        }
    }

    @Override
    public List<SkillFile> findByVersionId(Long versionId) {
        return gateway.selectFilesByVersion(versionId).stream()
                .map(entity -> new SkillFile(entity.getId(), entity.getSkillId(),
                        entity.getVersionId(), entity.getFilePath(), entity.getContent()))
                .toList();
    }

    @Override
    public void deleteByVersionId(Long versionId) {
        gateway.deleteFilesByVersion(versionId);
    }
}
