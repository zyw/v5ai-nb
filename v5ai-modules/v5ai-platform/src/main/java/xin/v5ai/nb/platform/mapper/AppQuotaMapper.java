package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.AppQuota;

import java.util.List;

@Mapper
public interface AppQuotaMapper extends BaseMapper<AppQuota> {
    default AppQuota selectByApp(String agentKey) {
        return selectOne(new LambdaQueryWrapper<AppQuota>().eq(AppQuota::getAgentKey, agentKey));
    }

    default List<AppQuota> selectAll() {
        return selectList(null);
    }
}
