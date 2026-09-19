package xin.v5ai.nb.runtime.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.runtime.core.config.properties.ConversationTitleProperties;
import xin.v5ai.nb.runtime.core.service.ConversationService;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型标题改写：改的是「系统生成」的名字、失败保留兜底名、关掉开关就完全不调模型。
 *
 * <p>改写跑在 Reactor 的 boundedElastic 上（不阻塞对话主链路），所以断言用闩锁等待，
 * 而不是假定它已经完成。</p>
 */
class ConversationTitleWriterTest {

    private static final long MODEL_ID = 7L;

    /** 假模型：只关心「有没有被调用」，返回什么由用例决定。 */
    private static final class FakeChatClient implements ModelChatClient {
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch called = new CountDownLatch(1);
        String answer = "图片内容识别";
        RuntimeException failure;

        @Override
        public String chatText(Long modelId, List<Msg> messages, Duration timeout) {
            calls.incrementAndGet();
            called.countDown();
            if (failure != null) {
                throw failure;
            }
            return answer;
        }

        @Override
        public ChatResponse firstResponse(Long modelId, List<Msg> messages, GenerateOptions options,
                                          Duration timeout) {
            throw new UnsupportedOperationException("标题改写只用 chatText");
        }
    }

    private FakeChatClient chatClient;
    private ConversationService conversationService;
    private ConversationTitleProperties properties;
    private ConversationTitleWriter writer;

    @BeforeEach
    void setUp() {
        chatClient = new FakeChatClient();
        conversationService = mock(ConversationService.class);
        properties = new ConversationTitleProperties();
        var resolver = mock(PublishedAgentResolver.class);
        when(resolver.resolve("a1"))
                .thenReturn(new AgentDTO("a1", "Demo", null, null, MODEL_ID, null, null));
        writer = new ConversationTitleWriter(resolver, chatClient, conversationService, properties);
    }

    @Test
    void theModelTitleReplacesTheFallbackName() throws Exception {
        var renamed = new CopyOnWriteArrayList<String>();
        var done = new CountDownLatch(1);
        when(conversationService.renameIfAutoNamed(anyString(), anyString())).thenAnswer(invocation -> {
            renamed.add(invocation.getArgument(0) + "|" + invocation.getArgument(1));
            done.countDown();
            return true;
        });

        writer.rewriteIfAutoNamed("c-1", "a1", "帮我看看这张图里有什么");

        assertThat(done.await(5, TimeUnit.SECONDS)).as("标题应该被回写").isTrue();
        assertThat(renamed).containsExactly("c-1|图片内容识别");
    }

    /** 模型挂了只是没拿到更好看的名字：兜底名已经在库里，不抛异常、不回写。 */
    @Test
    void aFailingModelKeepsTheFallbackName() throws Exception {
        chatClient.failure = new IllegalStateException("模型不可用");
        var renamed = new CountDownLatch(1);
        when(conversationService.renameIfAutoNamed(anyString(), anyString())).thenAnswer(invocation -> {
            renamed.countDown();
            return true;
        });

        writer.rewriteIfAutoNamed("c-1", "a1", "提问");

        assertThat(chatClient.called.await(5, TimeUnit.SECONDS)).as("确实调过模型").isTrue();
        assertThat(renamed.await(300, TimeUnit.MILLISECONDS)).isFalse();
        verify(conversationService, never()).renameIfAutoNamed(anyString(), anyString());
    }

    /** 模型回了一串没有信息量的标点：清洗后为空，同样保留兜底名。 */
    @Test
    void aTitleThatCleansUpToNothingKeepsTheFallbackName() throws Exception {
        chatClient.answer = "。。。";
        var renamed = new CountDownLatch(1);
        when(conversationService.renameIfAutoNamed(anyString(), anyString())).thenAnswer(invocation -> {
            renamed.countDown();
            return true;
        });

        writer.rewriteIfAutoNamed("c-1", "a1", "提问");

        assertThat(chatClient.called.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(renamed.await(300, TimeUnit.MILLISECONDS)).isFalse();
        verify(conversationService, never()).renameIfAutoNamed(anyString(), anyString());
    }

    @Test
    void theSwitchTurnsModelTitlesOff() throws Exception {
        properties.setEnabled(false);

        writer.rewriteIfAutoNamed("c-1", "a1", "提问");

        assertThat(chatClient.called.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(chatClient.calls).hasValue(0);
        verify(conversationService, never()).renameIfAutoNamed(anyString(), anyString());
    }

    /** 纯图片提问（没有文字）没有可命名的素材，不必白花一次调用。 */
    @Test
    void aQuestionWithoutTextIsNeverSentToTheModel() throws Exception {
        writer.rewriteIfAutoNamed("c-1", "a1", "   ");

        assertThat(chatClient.called.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(chatClient.calls).hasValue(0);
    }
}
