package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AppQuotaMapper extends BaseMapper<AppQuotaEntity> {
    default AppQuotaEntity selectByApp(String appKey) {
        return selectOne(new LambdaQueryWrapper<AppQuotaEntity>().eq(AppQuotaEntity::getAppKey, appKey));
    }

    default List<AppQuotaEntity> selectAll() {
        return selectList(null);
    }
}
