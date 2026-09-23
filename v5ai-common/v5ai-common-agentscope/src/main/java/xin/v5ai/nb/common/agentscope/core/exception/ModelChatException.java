package xin.v5ai.nb.common.agentscope.core.exception;

/**
 * 模型对话运行期失败（超时 / 连接失败 / 空响应）。
 * 与配置缺失（{@link IllegalArgumentException}）区分：
 * 本异常是运行期、可重试的操作失败；配置缺失是调用方或配置本身的错误。
 */
public class ModelChatException extends RuntimeException {

    public ModelChatException(String message) {
        super(message);
    }

    public ModelChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
