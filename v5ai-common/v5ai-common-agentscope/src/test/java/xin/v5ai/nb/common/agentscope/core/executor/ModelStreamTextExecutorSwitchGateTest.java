package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedMcpTool;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP/Skill 能力开关门控：关闭时不解析绑定（不产生任何解析副作用），开启时才查询解析器。
 * 仅验证解析器是否被调用，不订阅事件流（无需真实模型）。
 */
class ModelStreamTextExecutorSwitchGateTest {

    /** 计数解析器：记录被调用次数，返回空结果。 */
    private static final class CountingMcpResolver implements McpToolResolver {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<ResolvedMcpTool> resolveTools(Agent application) {
            calls.incrementAndGet();
            return List.of();
        }
    }

    private static final class CountingSkillResolver implements SkillWorkspaceResolver {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<ResolvedSkill> resolveSkills(Agent agent) {
            calls.incrementAndGet();
            return List.of();
        }
    }

    private static Agent agent(boolean mcpEnabled, boolean skillEnabled) {
        return new Agent("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null,
                false, mcpEnabled, skillEnabled, false, false, RagCallMode.FORCED.value());
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

        executor(workspace, mcp, skill).streamText(agent(false, false), new AgentRunRequest("agent-x", "c", "q"));

        assertThat(mcp.calls).hasValue(0);
        assertThat(skill.calls).hasValue(0);
    }

    @Test
    void mcpEnabledConsultsMcpResolver(@TempDir Path workspace) {
        var mcp = new CountingMcpResolver();
        var skill = new CountingSkillResolver();

        executor(workspace, mcp, skill).streamText(agent(true, false), new AgentRunRequest("agent-x", "c", "q"));

        assertThat(mcp.calls).hasValue(1);
        assertThat(skill.calls).hasValue(0);
    }

    @Test
    void skillEnabledConsultsSkillResolver(@TempDir Path workspace) {
        var mcp = new CountingMcpResolver();
        var skill = new CountingSkillResolver();

        executor(workspace, mcp, skill).streamText(agent(false, true), new AgentRunRequest("agent-x", "c", "q"));

        assertThat(mcp.calls).hasValue(0);
        assertThat(skill.calls).hasValue(1);
    }
}