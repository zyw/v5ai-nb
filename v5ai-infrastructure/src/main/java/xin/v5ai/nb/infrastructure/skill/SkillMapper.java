package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity>, SkillGateway {
    @Override
    default void insertSkill(SkillEntity entity) {
        insert(entity);
    }

    @Override
    default void updateSkill(SkillEntity entity) {
        updateById(entity);
    }

    @Override
    default SkillEntity selectSkillById(Long id) {
        return selectById(id);
    }

    @Override
    default SkillEntity selectSkillByName(String name) {
        return selectOne(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getName, name));
    }

    @Override
    default void disableSkill(Long id) {
        var entity = new SkillEntity();
        entity.setStatus("DISABLED");
        update(entity, new LambdaUpdateWrapper<SkillEntity>().eq(SkillEntity::getId, id));
    }

    @Override
    default List<SkillRow> selectAllWithCurrentVersion() {
        return selectAllSkillRows();
    }

    @Override
    default List<SkillRow> selectSkillOptions() {
        return selectSkillOptionRows();
    }

    @Select("""
            SELECT s.id, s.name, s.status, s.current_version_id,
                   v.version AS current_version
            FROM v5ai_skill s
            LEFT JOIN v5ai_skill_version v ON v.id = s.current_version_id
            ORDER BY s.id
            """)
    List<SkillRow> selectSkillOptionRows();

    @Override
    default SkillRow selectWithCurrentVersion(Long id) {
        return selectSkillRow(id);
    }

    @Select("""
            SELECT s.id, s.name, s.description, s.status, s.current_version_id,
                   v.version AS current_version, s.created_at, s.updated_at
            FROM v5ai_skill s
            LEFT JOIN v5ai_skill_version v ON v.id = s.current_version_id
            ORDER BY s.id
            """)
    List<SkillRow> selectAllSkillRows();

    @Select("""
            SELECT s.id, s.name, s.description, s.status, s.current_version_id,
                   v.version AS current_version, s.created_at, s.updated_at
            FROM v5ai_skill s
            LEFT JOIN v5ai_skill_version v ON v.id = s.current_version_id
            WHERE s.id = #{id}
            """)
    SkillRow selectSkillRow(Long id);
}
