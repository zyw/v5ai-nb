package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    /**
     * 原子累加当日用量（复合主键 upsert）：
     * 存在则 +1 次调用并累加 token，不存在则插入新行。避免读改写竞态与复合主键冲突。
     *
     * @param agentKey 应用（Agent）key
     * @param usageDate 日期
     * @param tokens    本次新增 token 数（非负数）
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO v5ai_app_usage (agent_key, usage_date, model_calls, tokens, updated_at)
            VALUES (#{agentKey}, #{usageDate}, 1, #{tokens}, now())
            ON CONFLICT (agent_key, usage_date) DO UPDATE
            SET model_calls = v5ai_app_usage.model_calls + 1,
                tokens = v5ai_app_usage.tokens + EXCLUDED.tokens,
                updated_at = now()
            """)
    int upsertUsage(@Param("agentKey") String agentKey,
                    @Param("usageDate") LocalDate usageDate,
                    @Param("tokens") long tokens);
}
