package xin.v5ai.nb.runtime.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.platform.api.UserService;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 门户初始化与头像代读的单测：只暴露「仍已发布」的 Agent，字段做归一化
 * （预设问题 JSON 串 → 列表、头像地址 → 前端可直接用/需代读）。
 */
class ChatAuthControllerTest {

    private PublishedAgentResolver resolver;
    private ResourceContentPort resourcePort;
    private ModelImageSupportResolver imageSupportResolver;
    private ChatAuthController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = mock(PublishedAgentResolver.class);
        resourcePort = mock(ResourceContentPort.class);
        imageSupportResolver = mock(ModelImageSupportResolver.class);
        var userService = mock(UserService.class);
        when(userService.selectNicknameById(7L)).thenReturn("张三");
        controller = new ChatAuthController(resolver, resourcePort, userService, imageSupportResolver);

        request = mock(HttpServletRequest.class);
        when(request.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE))
                .thenReturn(new ApiKeysAuthDTO(1L, 7L, "门户Key", "track-1", true, Set.of("a1", "a2")));
    }

    @Test
    void bootstrapSkipsUnavailableAgentAndMapsDisplayFields() {
        when(resolver.resolve("a1")).thenReturn(agent("a1", "/api/admin/resources/7/preview"));
        when(resolver.resolve("a2")).thenThrow(new AgentPublishException("agent is not published: a2"));

        when(imageSupportResolver.supportsImageInput(1L)).thenReturn(true);

        var data = controller.bootstrap(request).getData();

        assertThat(data.keyName()).isEqualTo("门户Key");
        assertThat(data.trackingId()).isEqualTo("track-1");
        assertThat(data.ownerName()).isEqualTo("张三");
        // 绑定里 a2 已不可用 → 门户只看到 a1
        assertThat(data.agents()).hasSize(1);
        var agent = data.agents().get(0);
        assertThat(agent.agentKey()).isEqualTo("a1");
        assertThat(agent.name()).isEqualTo("Demo");
        assertThat(agent.greeting()).isEqualTo("你好");
        assertThat(agent.presetQuestions()).containsExactly("Q1", "Q2");
        assertThat(agent.avatarUrl()).isEqualTo("/api/v1/agents/auth/a1/avatar");
        assertThat(agent.webSearchEnabled()).isTrue();
        // 附件入口的开关：所绑 CHAT 模型的 capabilities 含 image 才为真
        assertThat(agent.imageSupported()).isTrue();
    }

    /** 模型未声明 image 能力（或解析不到模型）时，门户据此禁用附件入口。 */
    @Test
    void bootstrapReportsImageUnsupportedWhenModelLacksTheCapability() {
        when(resolver.resolve("a1")).thenReturn(agent("a1", null));
        when(imageSupportResolver.supportsImageInput(1L)).thenReturn(false);

        assertThat(controller.bootstrap(request).getData().agents().get(0).imageSupported()).isFalse();
    }

    @Test
    void externalAvatarUrlIsReturnedAsIsAndUnknownShapeBecomesNull() {
        when(resolver.resolve("a1")).thenReturn(agent("a1", "https://cdn.example.com/logo.png"));
        assertThat(controller.bootstrap(request).getData().agents().get(0).avatarUrl())
                .isEqualTo("https://cdn.example.com/logo.png");

        when(resolver.resolve("a1")).thenReturn(agent("a1", "not-a-url"));
        assertThat(controller.bootstrap(request).getData().agents().get(0).avatarUrl()).isNull();
    }

    @Test
    void bootstrapToleratesBrokenPresetQuestionsJson() {
        when(resolver.resolve("a1")).thenReturn(new AgentDTO("a1", "Demo", "描述", AgentStatus.PUBLISHED, 1L, 1L,
                "sys", null, null, "{不是数组}", false, false, false, false, false, RagCallMode.FORCED.value(),
                null, true));

        assertThat(controller.bootstrap(request).getData().agents().get(0).presetQuestions()).isEmpty();
    }

    @Test
    void avatarIsStreamedForUploadedImage() {
        when(resolver.resolve("a1")).thenReturn(agent("a1", "/api/admin/resources/7/preview"));
        when(resourcePort.read(7L)).thenReturn(new ResourceContentPort.Content(new byte[]{1, 2}, "image/png", "logo.png"));

        var response = controller.avatar("a1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).containsExactly(1, 2);
    }

    @Test
    void avatarReturns404WhenAvatarIsNotAResourceOrAgentUnavailable() {
        when(resolver.resolve("a1")).thenReturn(agent("a1", "https://cdn.example.com/logo.png"));
        assertThat(controller.avatar("a1").getStatusCode().value()).isEqualTo(404);

        when(resolver.resolve("a2")).thenThrow(new AgentPublishException("agent is not published: a2"));
        assertThat(controller.avatar("a2").getStatusCode().value()).isEqualTo(404);
    }

    /**
     * 引用展示开关随 bootstrap 下发门户（供聊天窗口决定是否渲染引用折叠块）。
     *
     * <p>默认真值：存量 Agent（列默认 {@code true}）与存量快照（无该 key）都落到「展示」。</p>
     */
    @Test
    void bootstrapCarriesTheCitationsToggle() {
        when(resolver.resolve("a1")).thenReturn(agent("a1", null, true));
        assertThat(controller.bootstrap(request).getData().agents().get(0).showCitations()).isTrue();

        when(resolver.resolve("a1")).thenReturn(agent("a1", null, false));
        assertThat(controller.bootstrap(request).getData().agents().get(0).showCitations()).isFalse();
    }

    /**
     * 次要模型**不下发门户**：终端用户既不需要知道、也没有 UI 用得到它。
     *
     * <p>按契约（record 组件）断言而非按序列化结果——门户契约就是这个 record，
     * 谁把它加进来这条用例就会红，这正是想要的效果。</p>
     */
    @Test
    void secondaryModelIsNotPartOfThePortalContract() {
        var components = Arrays.stream(ChatAuthController.ChatAgent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components).doesNotContain("secondaryModelId");
        assertThat(components).contains("showCitations");
    }

    private AgentDTO agent(String agentKey, String avatar) {
        return agent(agentKey, avatar, true);
    }

    private AgentDTO agent(String agentKey, String avatar, boolean showCitations) {
        return new AgentDTO(agentKey, "Demo", "描述", AgentStatus.PUBLISHED, 1L, 1L, "sys",
                avatar, "你好", "[\"Q1\",\"Q2\"]", false, false, false, true, false, RagCallMode.FORCED.value(),
                null, showCitations);
    }
}
