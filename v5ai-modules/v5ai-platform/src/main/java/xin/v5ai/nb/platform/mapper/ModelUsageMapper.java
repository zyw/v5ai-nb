package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.ModelUsage;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ModelUsageMapper extends BaseMapper<ModelUsage> {
    default List<ModelUsage> selectByAppRange(String agentKey, Instant from, Instant to, int limit) {
        return selectList(new LambdaQueryWrapper<ModelUsage>()
                .eq(ModelUsage::getAgentKey, agentKey)
                .ge(ModelUsage::getCreatedAt, from)
                .le(ModelUsage::getCreatedAt, to)
                .orderByDesc(ModelUsage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
    }

    default List<ModelUsage> selectRecent(int limit) {
        return selectList(new LambdaQueryWrapper<ModelUsage>()
                .orderByDesc(ModelUsage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
    }
}
