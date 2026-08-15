package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SkillFileMapper extends BaseMapper<SkillFileEntity>, SkillFileGateway {
    @Override
    default void insertFile(SkillFileEntity entity) {
        insert(entity);
    }

    @Override
    default List<SkillFileEntity> selectFilesByVersion(Long versionId) {
        return selectList(new LambdaQueryWrapper<SkillFileEntity>()
                .eq(SkillFileEntity::getVersionId, versionId)
                .orderByAsc(SkillFileEntity::getFilePath));
    }

    @Override
    default void deleteFilesByVersion(Long versionId) {
        delete(new LambdaQueryWrapper<SkillFileEntity>().eq(SkillFileEntity::getVersionId, versionId));
    }
}
