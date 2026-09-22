package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * 基于 AgentScope HarnessAgent 的文本流式执行器。
 *
 * 把 Agent 配置（AgentDTO）与模型绑定到一个 HarnessAgent 上，
 * 以响应式流的形式对外暴露 Agent 的文本输出。
 *
 * 说明：这里刻意禁用了文件系统工具、Shell、记忆、子代理、技能等
 * 高级能力，只保留"模型直接回答"的最小运行形态。
 */
public class AgentScopeHarnessExecutor implements AgentTextExecutor {
    /** 绑定的模型（由 AgentModelResolver 解析得到） */
    private final Model model;
    /** 状态存储：保存 Agent 运行状态（会话/上下文） */
    private final AgentStateStore stateStore;
    /** 工作目录：Agent 可用的运行空间 */
    private final Path workspace;

    /**
     * 默认构造：状态存到 .agentscope/state，工作目录为 .agentscope/workspace。
     */
    public AgentScopeHarnessExecutor(Model model) {
        this(model, new JsonFileAgentStateStore(Path.of(".agentscope/state")), Path.of(".agentscope/workspace"));
    }

    /**
     * 指定状态存储，工作目录使用默认值。
     */
    public AgentScopeHarnessExecutor(Model model, AgentStateStore stateStore) {
        this(model, stateStore, Path.of(".agentscope/workspace"));
    }

    /**
     * 全参构造：模型 + 状态存储 + 工作目录均可自定义。
     */
    public AgentScopeHarnessExecutor(Model model, AgentStateStore stateStore, Path workspace) {
        this.model = model;
        this.stateStore = stateStore;
        this.workspace = workspace;
    }

    @Override
    public Flux<AgentTextEvent> streamText(AgentDTO agentDTO, AgentRunBo request) {
        // 确保工作目录存在
        ensureWorkspaceExists();
        // 按 Agent 信息构建 HarnessAgent，并关闭各类扩展能力（最小运行形态）
        HarnessAgent agent = HarnessAgent.builder()
                .name(agentDTO.name())
                .description("v5ai AgentScope Java minimum runtime")
                .model(model)
                .stateStore(stateStore)
                .workspace(workspace)
                // 禁用文件系统工具
                .disableFilesystemTools()
                // 禁用 Shell 工具
                .disableShellTool()
                // 禁用记忆工具
                .disableMemoryTools()
                // 禁用会话持久化
                .disableSessionPersistence()
                // 禁用工作区上下文
                .disableWorkspaceContext()
                // 禁用子代理
                .disableSubagents()
                // 禁用动态子代理
                .disableDynamicSubagents()
                // 禁用动态技能
                .disableDynamicSkills()
                // 禁用默认工作区技能
                .disableDefaultWorkspaceSkills()
                .build();
        return Flux.concat(
                        // 先发一个"模型调用开始"标记（供上层转为 MODEL_CALL 事件）
                        Flux.just(new AgentTextEvent.ModelCall(agentDTO.modelId(), model.getModelName(), model.getModelName())),
                        // 执行 Agent，把返回的消息转换为文本事件流；消息上还带着这次调用的真实用量
                        agent.call(request.query(), RuntimeContext.empty())
                                .flatMapMany(message -> {
                                    var events = new ArrayList<AgentTextEvent>(2);
                                    events.add(new AgentTextEvent.Text(message.getTextContent()));
                                    var usage = message.getChatUsage();
                                    if (usage != null) {
                                        events.add(new AgentTextEvent.Usage(
                                                usage.getInputTokens(), usage.getOutputTokens(), false));
                                    }
                                    return Flux.fromIterable(events);
                                }))
                // 流结束后（正常/异常/取消）关闭 Agent 释放资源
                .doFinally(signal -> agent.close());
    }

    /**
     * 确保工作目录存在，不存在则创建。
     */
    private void ensureWorkspaceExists() {
        try {
            Files.createDirectories(workspace);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create AgentScope workspace: " + workspace, exception);
        }
    }
}
