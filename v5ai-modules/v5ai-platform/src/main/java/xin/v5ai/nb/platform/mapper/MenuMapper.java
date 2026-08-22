package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.Menu;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<Menu> {
    default List<Menu> selectAll() {
        return selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSortOrder));
    }

    default Menu selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<Menu>().eq(Menu::getCode, code));
    }

    default Menu selectByRouteName(String routeName) {
        return selectOne(new LambdaQueryWrapper<Menu>().eq(Menu::getRouteName, routeName));
    }

    default long countChildren(Long parentId) {
        return selectCount(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, parentId));
    }

    default int updateMeta(Long id, Long parentId, String name, String type, String routeName,
                           String path, String icon, Integer sortOrder, Boolean visible) {
        return update(null, new LambdaUpdateWrapper<Menu>()
                .eq(Menu::getId, id)
                .set(Menu::getParentId, parentId)
                .set(Menu::getName, name)
                .set(Menu::getType, type)
                .set(Menu::getRouteName, routeName)
                .set(Menu::getPath, path)
                .set(Menu::getIcon, icon)
                .set(Menu::getSortOrder, sortOrder)
                .set(Menu::getVisible, visible)
                .set(Menu::getUpdatedAt, Instant.now()));
    }
}
