package xin.v5ai.nb.platform.mapper;

import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.ModelUsage;
import xin.v5ai.nb.platform.domain.vo.ModelUsageVo;


public interface ModelUsageMapper extends BaseMapperPlus<ModelUsage, ModelUsageVo> {
//    default List<ModelUsage> selectByAppRange(String agentKey, Instant from, Instant to, int limit) {
//        return selectList(new LambdaQueryWrapper<ModelUsage>()
//                .eq(ModelUsage::getAgentKey, agentKey)
//                .ge(ModelUsage::getCreatedAt, from)
//                .le(ModelUsage::getCreatedAt, to)
//                .orderByDesc(ModelUsage::getId)
//                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
//    }
//
//    default List<ModelUsage> selectRecent(int limit) {
//        return selectList(new LambdaQueryWrapper<ModelUsage>()
//                .orderByDesc(ModelUsage::getId)
//                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
//    }
}
