package xin.v5ai.nb.infrastructure.skill;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.skill.Skill;
import xin.v5ai.nb.skill.SkillOption;
import xin.v5ai.nb.skill.SkillRepository;
import xin.v5ai.nb.skill.SkillStatus;

import java.time.Instant;
import java.util.List;

@Repository
public class MyBatisSkillRepository implements SkillRepository {
    private final SkillGateway gateway;

    public MyBatisSkillRepository(SkillGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Skill save(Skill skill) {
        var entity = new SkillEntity();
        entity.setName(skill.name());
        entity.setDescription(skill.description());
        entity.setStatus(skill.status().name());
        entity.setCurrentVersionId(skill.currentVersionId());
        entity.setCreatedAt(skill.createdAt());
        entity.setUpdatedAt(skill.updatedAt());
        gateway.insertSkill(entity);
        return new Skill(entity.getId(), entity.getName(), entity.getDescription(),
                SkillStatus.valueOf(entity.getStatus()), entity.getCurrentVersionId(), null,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public Skill update(Skill skill) {
        var entity = new SkillEntity();
        entity.setId(skill.id());
        entity.setName(skill.name());
        entity.setDescription(skill.description());
        entity.setStatus(skill.status().name());
        entity.setCurrentVersionId(skill.currentVersionId());
        entity.setUpdatedAt(Instant.now());
        gateway.updateSkill(entity);
        return skill;
    }

    @Override
    public Skill findById(Long id) {
        return toDomain(gateway.selectWithCurrentVersion(id));
    }

    @Override
    public Skill findByName(String name) {
        var entity = gateway.selectSkillByName(name);
        if (entity == null) {
            return null;
        }
        return toDomain(gateway.selectWithCurrentVersion(entity.getId()));
    }

    @Override
    public List<Skill> list() {
        return gateway.selectAllWithCurrentVersion().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SkillOption> listOptions() {
        return gateway.selectSkillOptions().stream()
                .map(row -> new SkillOption(row.getId(), row.getName(),
                        SkillStatus.valueOf(row.getStatus()), row.getCurrentVersionId(), row.getCurrentVersion()))
                .toList();
    }

    @Override
    public void disable(Long id) {
        gateway.disableSkill(id);
    }

    private Skill toDomain(SkillRow row) {
        if (row == null) {
            return null;
        }
        return new Skill(row.getId(), row.getName(), row.getDescription(),
                SkillStatus.valueOf(row.getStatus()), row.getCurrentVersionId(), row.getCurrentVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}
