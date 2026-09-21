package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.*;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP/Skill 能力开关门控：关闭时不解析绑定（不产生任何解析副作用），开启时才查询解析器。
 * 仅验证解析器是否被调用，不订阅事件流（无需真实模型）。
 */
class ModelStreamTextExecutorSwitchGateTest {

    /** 计数解析器：记录被调用次数与收到的收窄集合，返回空结果。 */
    private static final class CountingMcpResolver implements McpToolResolver {
        final AtomicInteger calls = new AtomicInteger();
        final List<Collection<Long>> disabledSeen = new ArrayList<>();

        @Override
        public List<ResolvedMcpTool> resolveTools(AgentDTO application, Collection<Long> disabledServerIds) {
            calls.incrementAndGet();
            disabledSeen.add(disabledServerIds);
            return List.of();
        }
    }

    private static final class CountingSkillResolver implements SkillWorkspaceResolver {
        final AtomicInteger calls = new AtomicInteger();
        final List<Collection<Long>> disabledSeen = new ArrayList<>();

        @Override
        public List<ResolvedSkill> resolveSkills(AgentDTO agent, Collection<Long> disabledSkillIds) {
            calls.incrementAndGet();
            disabledSeen.add(disabledSkillIds);
            return List.of();
        }
    }

    private static AgentDTO agent(boolean mcpEnabled, boolean skillEnabled) {
        return new AgentDTO("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null,
                false, mcpEnabled, skillEnabled, false, false, RagCallMode.FORCED.value(),
                null, true);
    }

    private static ModelStreamTextExecutor executor(Path workspace, McpToolResolver mcp, SkillWorkspaceResolver skill) {
        // streamDirect 会在调用期即构建管道（含 model.stream），故 mock 模型需返回空 Flux
        var model = Mockito.mock(Model.class);
        Mockito.when(model.stream(Mockito.anyList(), Mockito.anyList(), Mockito.any(GenerateOptions.class)))
                .thenReturn(Flux.empty());
        return new ModelStreamTextExecutor(model, mcp, null, skill, workspace, "tvly-key");
    }

    @Test
    void disabledSwitchesSkipResolversEntirely(@TempDir Path workspace) {
        var mcp = new CountingMcpResolver();
        var skill = new CountingSkillResolver();

        executor(workspace, mcp, skill).streamText(agent(false, false), new AgentRunBo("agent-x", "c", "q"));

        assertThat(mcp.calls).hasValue(0);
        assertThat(skill.calls).hasValue(0);
    }

    @Test
    void mcpEnabledConsultsMcpResolver(@TempDir Path workspace) {
        var mcp = new CountingMcpResolver();
        var skill = new CountingSkillResolver();

        executor(workspace, mcp, skill).streamText(agent(true, false), new AgentRunBo("agent-x", "c", "q"));

        assertThat(mcp.calls).hasValue(1);
        assertThat(skill.calls).hasValue(0);
    }

    @Test
    void skillEnabledConsultsSkillResolver(@TempDir Path workspace) {
        var mcp = new CountingMcpResolver();
        var skill = new CountingSkillResolver();

        executor(workspace, mcp, skill).streamText(agent(false, true), new AgentRunBo("agent-x", "c", "q"));

        assertThat(mcp.calls).hasValue(0);
        assertThat(skill.calls).hasValue(1);
    }

    /**
     * 收窄项只能减少、不能增加：请求里显式禁用的 Server/Skill 会被原样交给解析器，
     * 由解析器在建立连接/读取版本文件之前排除掉。
     */
    @Test
    void disabledIdsAreHandedToTheResolvers(@TempDir Path workspace) {
        var mcp = new CountingMcpResolver();
        var skill = new CountingSkillResolver();

        executor(workspace, mcp, skill).streamText(agent(true, true),
                new AgentRunBo("agent-x", "c", "q")
                        .withDisabledMcpServerIds(List.of(7L))
                        .withDisabledSkillIds(List.of(9L)));

        assertThat(mcp.disabledSeen).containsExactly(List.of(7L));
        assertThat(skill.disabledSeen).containsExactly(List.of(9L));
    }
}