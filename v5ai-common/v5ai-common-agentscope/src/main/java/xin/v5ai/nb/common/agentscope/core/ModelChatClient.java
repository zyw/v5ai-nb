package xin.v5ai.nb.common.agentscope.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;

import java.time.Duration;
import java.util.List;

/**
 * 模型对话端口：按模型 ID 发起一次文本补全调用（管理端 AI、部署期配置生成、连通性测试共用）。
 * 实现负责「解析运行时配置 → 解密凭据 → 构建模型 → 发起对话」整条链，
 * 调用方无需了解凭据、AgentScope 模型构造与文本拼接细节。
 * <p>
 * 错误契约：
 * <ul>
 *   <li>配置缺失（模型不存在/运行时配置查不到）：{@link IllegalArgumentException}</li>
 *   <li>超时 / 连接失败 / 空响应（运行期、可重试的操作失败）：{@link ModelChatException}</li>
 * </ul>
 */
public interface ModelChatClient {

    /**
     * 全量文本补全：流式完成并拼接全部文本块后返回。
     *
     * @param modelId  已配置的 Chat 模型 ID
     * @param messages 消息列表（通常 SYSTEM + USER）
     * @param timeout  整体超时（由调用方按场景传入）
     * @return 拼接后的完整文本（非空）
     * @throws IllegalArgumentException 配置缺失
     * @throws ModelChatException       超时 / 连接失败 / 空响应
     */
    String chatText(Long modelId, List<Msg> messages, Duration timeout);

    /**
     * 首个响应块即返回（连通性 / 流式探测用），不等待流完成。
     *
     * @param modelId  已配置的 Chat 模型 ID
     * @param messages 消息列表
     * @param options  生成选项（调用方可限制 maxTokens 等）
     * @param timeout  首块等待超时
     * @return 第一个响应块
     * @throws IllegalArgumentException 配置缺失
     * @throws ModelChatException       超时 / 连接失败 / 无响应
     */
    ChatResponse firstResponse(Long modelId, List<Msg> messages, GenerateOptions options, Duration timeout);
}
