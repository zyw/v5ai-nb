package xin.v5ai.nb.common.agentscope.utils;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;

/**
 * {@link ChatResponse} 文本提取的共享工具：把单个响应中的全部 TextBlock 文本拼接返回。
 * 供运行时流式执行器与模型对话实现共用，消除拼文本的重复（locality）。
 */
public final class ChatResponseTexts {

    private ChatResponseTexts() {
    }

    /**
     * 拼接单个响应的全部 TextBlock 文本。
     *
     * @param response 模型响应分片
     * @return 拼接文本；response 为空或 content 为空返回 null
     */
    public static String textOf(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}
