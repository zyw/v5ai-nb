package xin.v5ai.nb.infrastructure.skill;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.skill.SkillVersion;
import xin.v5ai.nb.skill.SkillVersionRepository;
import xin.v5ai.nb.skill.SkillVersionStatus;

import java.time.Instant;
import java.util.List;

@Repository
public class MyBatisSkillVersionRepository implements SkillVersionRepository {
    private final SkillVersionGateway gateway;

    public MyBatisSkillVersionRepository(SkillVersionGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public SkillVersion save(SkillVersion version) {
        var entity = new SkillVersionEntity();
        entity.setSkillId(version.skillId());
        entity.setVersion(version.version());
        entity.setStatus(version.status().name());
        entity.setDescription(version.description());
        entity.setPublishedAt(version.publishedAt());
        entity.setCreatedAt(version.createdAt());
        gateway.insertVersion(entity);
        return new SkillVersion(entity.getId(), entity.getSkillId(), entity.getVersion(),
                SkillVersionStatus.valueOf(entity.getStatus()), entity.getDescription(),
                entity.getPublishedAt(), entity.getCreatedAt());
    }

    @Override
    public SkillVersion findById(Long id) {
        return toDomain(gateway.selectVersionById(id));
    }

    @Override
    public List<SkillVersion> findBySkillId(Long skillId) {
        return gateway.selectVersionsBySkill(skillId).stream().map(this::toDomain).toList();
    }

    @Override
    public long nextVersion(Long skillId) {
        return gateway.selectVersionsBySkill(skillId).stream()
                .mapToLong(SkillVersionEntity::getVersion)
                .max()
                .orElse(0L) + 1;
    }

    @Override
    public void updateStatus(Long versionId, SkillVersionStatus status, Instant publishedAt) {
        gateway.updateVersionStatus(versionId, status.name(), publishedAt);
    }

    private SkillVersion toDomain(SkillVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SkillVersion(entity.getId(), entity.getSkillId(), entity.getVersion(),
                SkillVersionStatus.valueOf(entity.getStatus()), entity.getDescription(),
                entity.getPublishedAt(), entity.getCreatedAt());
    }
}
