package xin.v5ai.nb.agent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.bo.ChatBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端预览/调试入参：附件必须原样进运行时（否则前端选好图、后端悄悄丢掉），
 * 且与线上门户同一条图片能力门控——模型不支持就 400，而不是让它以模型报错收场。
 */
class AgentDebugControllerTest {

    private static final String AGENT_KEY = "a1";

    private AgentRuntime runtime;
    private PublishedAgentResolver publishedAgentResolver;
    private ModelImageSupportResolver imageSupportResolver;
    private AgentDebugController controller;

    @BeforeEach
    void setUp() {
        runtime = mock(AgentRuntime.class);
        publishedAgentResolver = mock(PublishedAgentResolver.class);
        imageSupportResolver = mock(ModelImageSupportResolver.class);
        controller = new AgentDebugController(runtime, publishedAgentResolver, imageSupportResolver);
    }

    @Test
    void attachmentsAreForwardedToTheRun() {
        when(publishedAgentResolver.resolveForDebug(AGENT_KEY)).thenReturn(agent(7L));
        when(imageSupportResolver.supportsImageInput(7L)).thenReturn(true);
        when(runtime.stream(any())).thenReturn(Flux.empty());

        controller.stream(AGENT_KEY, chatWithImage(42L));

        var captor = ArgumentCaptor.forClass(AgentRunBo.class);
        verify(runtime).stream(captor.capture());
        assertThat(captor.getValue().attachments())
                .extracting(AttachmentRef::resourceId)
                .containsExactly(42L);
    }

    /** 调试面板按草稿态解析 Agent：未发布的智能体也要能校验模型能力。 */
    @Test
    void theCapabilityCheckUsesTheDraftAgent() {
        when(publishedAgentResolver.resolveForDebug(AGENT_KEY)).thenReturn(agent(7L));
        when(imageSupportResolver.supportsImageInput(7L)).thenReturn(true);
        when(runtime.stream(any())).thenReturn(Flux.empty());

        controller.stream(AGENT_KEY, chatWithImage(42L));

        verify(publishedAgentResolver).resolveForDebug(AGENT_KEY);
    }

    @Test
    void attachmentsAreRejectedWhenTheModelCannotReadImages() {
        when(publishedAgentResolver.resolveForDebug(AGENT_KEY)).thenReturn(agent(7L));
        when(imageSupportResolver.supportsImageInput(7L)).thenReturn(false);

        var failure = catchThrowableOfType(() -> controller.stream(AGENT_KEY, chatWithImage(42L)),
                V5aiException.class);

        assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(runtime, never()).stream(any());
    }

    /** 不带附件的普通调试：一次模型查询都不该发生（每个 token 都走这条路）。 */
    @Test
    void runsWithoutAttachmentsNeverLookUpTheModel() {
        when(runtime.stream(any())).thenReturn(Flux.empty());

        controller.stream(AGENT_KEY, new ChatBo("c-1", "你好", null, List.of(), List.of(), List.of()));

        verifyNoInteractions(publishedAgentResolver, imageSupportResolver);
    }

    private static ChatBo chatWithImage(Long resourceId) {
        return new ChatBo("c-1", "这张图里是什么", null,
                List.of(AttachmentRef.image(resourceId)), List.of(), List.of());
    }

    private static AgentDTO agent(Long modelId) {
        return new AgentDTO(AGENT_KEY, "Demo", "desc", null, modelId, null, null);
    }
}
