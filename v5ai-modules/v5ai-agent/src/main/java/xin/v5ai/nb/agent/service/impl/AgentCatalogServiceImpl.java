package xin.v5ai.nb.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.agent.api.AgentCatalogService;
import xin.v5ai.nb.agent.mapper.AgentMapper;

import java.util.Collection;
import java.util.Set;

/**
 * {@link AgentCatalogService} 的 MyBatis-Plus 实现：从数据库筛出 PUBLISHED 状态的 agentKey。
 *
 * <p>供 API Key 绑定等写入路径做「只能绑定已发布 Agent」的服务端校验，
 * 放在 v5ai-agent（数据归属方）内，避免其它模块直接读 v5ai_agent 表。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentCatalogServiceImpl implements AgentCatalogService {

    private final AgentMapper agentMapper;

    @Override
    public Set<String> filterPublished(Collection<String> agentKeys) {
        if (CollUtil.isEmpty(agentKeys)) {
            return Set.of();
        }
        return agentMapper.selectPublishedKeys(agentKeys);
    }
}