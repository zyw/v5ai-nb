package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AppUsageMapper extends BaseMapper<AppUsageEntity> {
    default AppUsageEntity selectByAppAndDate(String agentKey, LocalDate date) {
        return selectOne(new LambdaQueryWrapper<AppUsageEntity>()
                .eq(AppUsageEntity::getAgentKey, agentKey)
                .eq(AppUsageEntity::getUsageDate, date));
    }

    default List<AppUsageEntity> selectByAppRange(String agentKey, LocalDate from, LocalDate to) {
        return selectList(new LambdaQueryWrapper<AppUsageEntity>()
                .eq(AppUsageEntity::getAgentKey, agentKey)
                .ge(AppUsageEntity::getUsageDate, from)
                .le(AppUsageEntity::getUsageDate, to)
                .orderByAsc(AppUsageEntity::getUsageDate));
    }
}
