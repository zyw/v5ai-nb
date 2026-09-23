package xin.v5ai.nb.platform.mapper;

import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.V5aiModelUsage;
import xin.v5ai.nb.platform.domain.vo.V5aiModelUsageVo;


public interface V5aiModelUsageMapper extends BaseMapperPlus<V5aiModelUsage, V5aiModelUsageVo> {
//    default List<V5aiModelUsage> selectByAppRange(String agentKey, Instant from, Instant to, int limit) {
//        return selectList(new LambdaQueryWrapper<V5aiModelUsage>()
//                .eq(V5aiModelUsage::getAgentKey, agentKey)
//                .ge(V5aiModelUsage::getCreatedAt, from)
//                .le(V5aiModelUsage::getCreatedAt, to)
//                .orderByDesc(V5aiModelUsage::getId)
//                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
//    }
//
//    default List<V5aiModelUsage> selectRecent(int limit) {
//        return selectList(new LambdaQueryWrapper<V5aiModelUsage>()
//                .orderByDesc(V5aiModelUsage::getId)
//                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
//    }
}
