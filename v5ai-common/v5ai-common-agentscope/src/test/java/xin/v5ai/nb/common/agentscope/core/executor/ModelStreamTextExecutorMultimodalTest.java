package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.service.AttachmentContentProvider;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多模态消息组装：附件真正变成模型输入里的图片块，并且历史回放受体积预算约束
 * （按最近优先消费，超出的图片退化为 {@code [图片]} 占位）。
 */
class ModelStreamTextExecutorMultimodalTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private Model model;
    private AttachmentContentProvider attachmentProvider;

    @BeforeEach
    void setUp() {
        model = mock(Model.class);
        attachmentProvider = mock(AttachmentContentProvider.class);
        when(model.stream(anyList(), anyList(), any(GenerateOptions.class))).thenReturn(Flux.empty());
    }

    private ModelStreamTextExecutor executor(long budgetBytes) {
        return new ModelStreamTextExecutor(model, null, null, null, null, null, null,
                attachmentProvider, budgetBytes);
    }

    private static AgentDTO agent() {
        return new AgentDTO("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null, false, false, false, false, false, RagCallMode.FORCED.value(),
                null, true);
    }

    @Test
    void currentQueryImageBecomesAnImageBlockNextToTheText() {
        when(attachmentProvider.read(5L))
                .thenReturn(new AttachmentContentProvider.AttachmentContent(PNG, "image/png"));

        executor(1024).streamText(agent(), new AgentRunBo("agent-x", "c", "这是什么")
                .withAttachments(List.of(AttachmentRef.image(5L))));

        var user = lastMessage();
        assertThat(user.getRole()).isEqualTo(MsgRole.USER);
        assertThat(user.getContentBlocks(TextBlock.class)).extracting(TextBlock::getText)
                .containsExactly("这是什么");
        var images = user.getContentBlocks(ImageBlock.class);
        assertThat(images).hasSize(1);
        var source = (Base64Source) images.get(0).getSource();
        assertThat(source.getMediaType()).isEqualTo("image/png");
        assertThat(Base64.getDecoder().decode(source.getData())).isEqualTo(PNG);
    }

    @Test
    void historicalImagesAreReplayedWhenTheyFitTheBudget() {
        when(attachmentProvider.read(5L))
                .thenReturn(new AttachmentContentProvider.AttachmentContent(PNG, "image/png"));
        when(attachmentProvider.read(6L))
                .thenReturn(new AttachmentContentProvider.AttachmentContent(PNG, "image/png"));

        executor(1024).streamText(agent(), new AgentRunBo("agent-x", "c", "第二张")
                .withAttachments(List.of(AttachmentRef.image(6L)))
                .withHistory(List.of(historyWithImage(5L))));

        var messages = captureMessages();
        var historical = messages.get(messages.size() - 2);
        assertThat(historical.getContentBlocks(ImageBlock.class)).hasSize(1);
        assertThat(lastMessage().getContentBlocks(ImageBlock.class)).hasSize(1);
    }

    /**
     * 预算被当前提问用尽时，历史图片**根本不会被读取**（不是读完再丢），
     * 在上下文里退化为 {@code [图片]} 占位——这是 Q21 的取舍：更早的图会消失。
     */
    @Test
    void imagesBeyondTheBudgetBecomePlaceholderWithoutBeingRead() {
        when(attachmentProvider.read(6L))
                .thenReturn(new AttachmentContentProvider.AttachmentContent(new byte[600], "image/png"));

        executor(600).streamText(agent(), new AgentRunBo("agent-x", "c", "新图")
                .withAttachments(List.of(AttachmentRef.image(6L)))
                .withHistory(List.of(historyWithImage(5L))));

        var messages = captureMessages();
        var historical = messages.get(messages.size() - 2);
        assertThat(historical.getContentBlocks(ImageBlock.class)).isEmpty();
        assertThat(historical.getContentBlocks(TextBlock.class)).extracting(TextBlock::getText)
                .contains("旧图", ModelStreamTextExecutor.IMAGE_PLACEHOLDER);
        assertThat(lastMessage().getContentBlocks(ImageBlock.class)).hasSize(1);
        verify(attachmentProvider, never()).read(5L);
    }

    @Test
    void unreadableImageDegradesToPlaceholderWithoutFailingTheRun() {
        when(attachmentProvider.read(5L)).thenThrow(new IllegalStateException("资源不存在: 5"));

        executor(1024).streamText(agent(), new AgentRunBo("agent-x", "c", "看图")
                .withAttachments(List.of(AttachmentRef.image(5L))));

        var user = lastMessage();
        assertThat(user.getContentBlocks(ImageBlock.class)).isEmpty();
        assertThat(user.getContentBlocks(TextBlock.class)).extracting(TextBlock::getText)
                .contains("看图", ModelStreamTextExecutor.IMAGE_PLACEHOLDER);
    }

    /** 未装配附件读取端口时行为与改造前完全一致：附件被忽略，消息仍是纯文本。 */
    @Test
    void withoutAProviderAttachmentsAreIgnoredAndMessagesStayPlainText() {
        var executor = new ModelStreamTextExecutor(model, null, null, null, null, null, null);

        executor.streamText(agent(), new AgentRunBo("agent-x", "c", "看图")
                .withAttachments(List.of(AttachmentRef.image(5L))));

        var user = lastMessage();
        assertThat(user.getContentBlocks(ImageBlock.class)).isEmpty();
        assertThat(user.getTextContent()).isEqualTo("看图");
        verify(attachmentProvider, never()).read(any());
    }

    private SessionMessage historyWithImage(long resourceId) {
        return new SessionMessage("c", "agent-x", MessageRole.USER, "旧图",
                List.of(AttachmentRef.image(resourceId)));
    }

    private Msg lastMessage() {
        var messages = captureMessages();
        return messages.get(messages.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<Msg> captureMessages() {
        ArgumentCaptor<List<Msg>> captor = ArgumentCaptor.forClass(List.class);
        verify(model, atLeastOnce()).stream(captor.capture(), anyList(), any(GenerateOptions.class));
        return captor.getValue();
    }
}
