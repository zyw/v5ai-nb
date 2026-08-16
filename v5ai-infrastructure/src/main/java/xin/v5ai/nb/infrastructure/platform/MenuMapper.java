package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {
    default List<MenuEntity> selectAll() {
        return selectList(new LambdaQueryWrapper<MenuEntity>().orderByAsc(MenuEntity::getSortOrder));
    }

    default MenuEntity selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getCode, code));
    }

    default MenuEntity selectByRouteName(String routeName) {
        return selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getRouteName, routeName));
    }

    default long countChildren(Long parentId) {
        return selectCount(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getParentId, parentId));
    }

    default int updateMeta(Long id, Long parentId, String name, String type, String routeName,
                           String path, String icon, Integer sortOrder, Boolean visible) {
        return update(null, new LambdaUpdateWrapper<MenuEntity>()
                .eq(MenuEntity::getId, id)
                .set(MenuEntity::getParentId, parentId)
                .set(MenuEntity::getName, name)
                .set(MenuEntity::getType, type)
                .set(MenuEntity::getRouteName, routeName)
                .set(MenuEntity::getPath, path)
                .set(MenuEntity::getIcon, icon)
                .set(MenuEntity::getSortOrder, sortOrder)
                .set(MenuEntity::getVisible, visible)
                .set(MenuEntity::getUpdatedAt, Instant.now()));
    }
}
