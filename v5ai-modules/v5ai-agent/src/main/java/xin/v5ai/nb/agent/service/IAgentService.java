package xin.v5ai.nb.agent.service;

import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVersionVo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * Agent 管理服务：创建/更新/禁用/删除、分页与列表查询、版本列表与发布。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public interface IAgentService {

    /**
     * 分页查询 Agent。
     */
    PageResult<AgentVo> queryPageList(AgentBo bo, PageQuery pageQuery);

    /**
     * 查询 Agent 列表。
     */
    List<AgentVo> queryList(AgentBo bo);

    /**
     * 查询单个 Agent。
     */
    AgentVo getAgent(String agentKey);

    /**
     * 创建 Agent（草稿）：校验必填与 agentKey 查重。
     */
    AgentVo createAgent(AgentBo bo);

    /**
     * 更新 Agent：按 agentKey 合并字段（未传字段保留原值）。
     */
    AgentVo updateAgent(String agentKey, AgentBo bo);

    /**
     * 禁用 Agent。
     */
    void disable(String agentKey);

    /**
     * 删除 Agent 及其全部关联数据。
     */
    void delete(String agentKey);

    /**
     * 查询某 Agent 的版本列表（新版本在前）。
     */
    List<AgentVersionVo> listVersions(String agentKey);

    /**
     * 发布 Agent：校验模型已启用 → 生成配置快照 → 保存版本并置为 PUBLISHED。
     */
    void publish(String agentKey, String description);
}
