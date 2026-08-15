package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

@Mapper
public interface SkillVersionMapper extends BaseMapper<SkillVersionEntity>, SkillVersionGateway {
    @Override
    default void insertVersion(SkillVersionEntity entity) {
        insert(entity);
    }

    @Override
    default void updateVersionStatus(Long versionId, String status, Instant publishedAt) {
        var entity = new SkillVersionEntity();
        entity.setStatus(status);
        entity.setPublishedAt(publishedAt);
        update(entity, new LambdaUpdateWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getId, versionId));
    }

    @Override
    default SkillVersionEntity selectVersionById(Long id) {
        return selectById(id);
    }

    @Override
    default List<SkillVersionEntity> selectVersionsBySkill(Long skillId) {
        return selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .orderByDesc(SkillVersionEntity::getVersion));
    }
}
