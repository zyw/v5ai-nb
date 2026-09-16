package xin.v5ai.nb.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.agent.domain.AgentVersion;

import java.util.List;

/**
 * <p>
 * AgentDTO 版本 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersion> {

    /**
     * 计算某 AgentDTO 的下一个版本号。
     *
     * @param agentKey AgentDTO 的 agentKey
     * @return 下一个版本号（1 起）
     */
    Long nextVersion(@Param("agentKey") String agentKey);

    /**
     * 按 agentKey 查询版本列表（新版本在前）。
     *
     * @param agentKey AgentDTO 的 agentKey
     * @return 版本列表
     */
    default List<AgentVersion> selectVersionsByAgentKey(String agentKey) {
        return selectList(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentKey, agentKey)
                .orderByDesc(AgentVersion::getVersion));
    }
}
