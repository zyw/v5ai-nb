package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.V5aiAppQuota;
import xin.v5ai.nb.platform.domain.vo.V5aiAppQuotaVo;

import java.util.List;

@Mapper
public interface V5aiAppQuotaMapper extends BaseMapperPlus<V5aiAppQuota, V5aiAppQuotaVo> {

    default V5aiAppQuota selectByApp(String agentKey) {
        return selectOne(new LambdaQueryWrapper<V5aiAppQuota>().eq(V5aiAppQuota::getAgentKey, agentKey));
    }

    default List<V5aiAppQuota> selectAll() {
        return selectList(null);
    }
}
