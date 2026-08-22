package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.AppUsage;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AppUsageMapper extends BaseMapper<AppUsage> {
    default AppUsage selectByAppAndDate(String agentKey, LocalDate date) {
        return selectOne(new LambdaQueryWrapper<AppUsage>()
                .eq(AppUsage::getAgentKey, agentKey)
                .eq(AppUsage::getUsageDate, date));
    }

    default List<AppUsage> selectByAppRange(String agentKey, LocalDate from, LocalDate to) {
        return selectList(new LambdaQueryWrapper<AppUsage>()
                .eq(AppUsage::getAgentKey, agentKey)
                .ge(AppUsage::getUsageDate, from)
                .le(AppUsage::getUsageDate, to)
                .orderByAsc(AppUsage::getUsageDate));
    }
}
