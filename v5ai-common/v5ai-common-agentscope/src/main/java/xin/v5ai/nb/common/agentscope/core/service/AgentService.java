package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentVersionDTO;

import java.util.List;

public interface AgentService {

    /**
     * 根据 Agent Key 查询 AgentDTO。
     *
     * @param agentKey Agent Key
     * @return AgentDTO
     */
    AgentDTO findByAgentKey(String agentKey);

    /**
     * 保存 AgentDTO。
     *
     * @param agent AgentDTO
     */
    void save(AgentDTO agent);

    /**
     * 更新 AgentDTO。
     *
     * @param agent AgentDTO
     */
    void update(AgentDTO agent);

    /**
     * 禁用 AgentDTO。
     *
     * @param agentKey Agent Key
     */
    void disable(String agentKey);

    /**
     * 删除 AgentDTO 及其全部关联数据（版本/绑定/API Key/会话/运行/用量等）。
     *
     * @param agentKey Agent Key
     */
    void delete(String agentKey);

    /**
     * 列出所有 AgentDTO。
     *
     * @return AgentDTO 列表
     */
    List<AgentDTO> list();

    /**
     * 获取下一个 Agent 版本号。
     *
     * @param agentKey Agent Key
     * @return 下一个 Agent 版本号
     */
    long nextVersion(String agentKey);

    /**
     * 保存 Agent 版本。
     *
     * @param version Agent 版本
     */
    void saveVersion(AgentVersionDTO version);

    /**
     * 列出所有 Agent 版本。
     *
     * @param agentKey Agent Key
     * @return Agent 版本列表
     */
    List<AgentVersionDTO> listVersions(String agentKey);

    /**
     * 发布 Agent 版本。
     *
     * @param agentKey Agent Key
     * @param version Agent 版本号
     */
    void publish(String agentKey, long version);
}
