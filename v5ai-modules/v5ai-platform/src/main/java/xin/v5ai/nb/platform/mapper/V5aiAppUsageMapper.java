package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.V5aiAppUsage;
import xin.v5ai.nb.platform.domain.vo.V5aiAppUsageVo;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface V5aiAppUsageMapper extends BaseMapperPlus<V5aiAppUsage, V5aiAppUsageVo> {

    default V5aiAppUsage selectByAppAndDate(String agentKey, LocalDate date) {
        return selectOne(new LambdaQueryWrapper<V5aiAppUsage>()
                .eq(V5aiAppUsage::getAgentKey, agentKey)
                .eq(V5aiAppUsage::getUsageDate, date));
    }

    default List<V5aiAppUsage> selectByAppRange(String agentKey, LocalDate from, LocalDate to) {
        return selectList(new LambdaQueryWrapper<V5aiAppUsage>()
                .eq(V5aiAppUsage::getAgentKey, agentKey)
                .ge(V5aiAppUsage::getUsageDate, from)
                .le(V5aiAppUsage::getUsageDate, to)
                .orderByAsc(V5aiAppUsage::getUsageDate));
    }
}
