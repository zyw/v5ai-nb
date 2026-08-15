package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApplicationSkillBindingMapper extends BaseMapper<ApplicationSkillBindingEntity>,
        ApplicationSkillBindingGateway {
    @Override
    default void insertBinding(ApplicationSkillBindingEntity entity) {
        insert(entity);
    }

    @Override
    default void deleteByAppKey(String appKey) {
        delete(new LambdaQueryWrapper<ApplicationSkillBindingEntity>()
                .eq(ApplicationSkillBindingEntity::getAppKey, appKey));
    }

    @Override
    default List<ApplicationSkillBindingEntity> selectByAppKey(String appKey) {
        return selectList(new LambdaQueryWrapper<ApplicationSkillBindingEntity>()
                .eq(ApplicationSkillBindingEntity::getAppKey, appKey));
    }
}
