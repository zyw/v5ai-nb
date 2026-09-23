package xin.v5ai.nb.runtime.controller;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelImageSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.platform.api.UserService;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 对话门户（终端用户侧）鉴权与初始化入口：用 API Key 鉴权，而不是管理端登录态。
 *
 * <p>全部端点都在 {@code /api/v1/agents/auth/**} 下（既有过滤器注册的前缀内），
 * 鉴权由 {@code AgentApiKeysServletFilter} 完成、结果放在请求属性里；本控制器只负责组装数据：</p>
 * <ul>
 *   <li>{@code GET /api/v1/agents/auth/bootstrap}：Key 信息（名称/跟踪 ID/归属人）
 *       + 该 Key 可访问且**仍已发布**的 Agent 展示信息。放在 {@code /agents} 前缀下是为了
 *       复用既有过滤器注册（不带 agentKey，因此只验 Key）；</li>
 *   <li>{@code GET /api/v1/agents/auth/{agentKey}/avatar}：Agent 头像（需绑定该 agentKey，不计配额）。上传的
 *       头像存的是管理端受鉴权地址（{@code /api/admin/resources/{id}/preview}），门户拿不到管理端令牌，故用 Key 鉴权代读。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agents/auth")
public class ChatAuthController {

    /** 资源访问地址形如 /api/admin/resources/{id}/preview（见 PlmResourceServiceImpl#buildAccessUrl）。 */
    private static final Pattern RESOURCE_PATH = Pattern.compile("^/api/admin/resources/(\\d+)/");

    private final PublishedAgentResolver publishedAgentResolver;
    private final ResourceContentPort resourceContentPort;
    private final UserService userService;
    private final ModelImageSupportResolver modelImageSupportResolver;

    /**
     * 门户 Agent 展示信息（只暴露终端用户需要看到的字段，不含系统提示词等运行配置）。
     *
     * @param agentKey        运行标识
     * @param name            名称
     * @param description     描述
     * @param avatarUrl       可直接使用的头像地址（外部 http(s) 原样返回；上传的图片返回门户代读地址）；无头像为 null
     * @param greeting        开场白
     * @param presetQuestions 预设问题（已把快照里的 JSON 数组解析成列表）
     * @param webSearchEnabled Agent 是否允许联网搜索（门户开关据此启用/禁用）
     * @param imageSupported   所绑 CHAT 模型是否支持图片输入（门户据此启用/禁用附件入口）
     * @param showCitations    是否展示 RAG 引用折叠块（仅影响渲染；引用照常检索与落库）
     */
    public record ChatAgent(String agentKey, String name, String description, String avatarUrl,
                              String greeting, List<String> presetQuestions, boolean webSearchEnabled,
                              boolean imageSupported, boolean showCitations) {
    }

    /**
     * 门户初始化数据。
     *
     * @param keyName   Key 名称
     * @param trackingId 跟踪 ID（展示/对账用）
     * @param ownerName 归属用户昵称（取不到时为 null）
     * @param agents    该 Key 可访问且仍已发布的 Agent
     */
    public record ChatBootstrap(String keyName, String trackingId, String ownerName, List<ChatAgent> agents) {
    }

    /**
     * 门户初始化：校验通过后拿到当前 Key 的信息与可用 Agent 列表。
     */
    @GetMapping("/bootstrap")
    public R<ChatBootstrap> bootstrap(HttpServletRequest request) {
        var auth = (ApiKeysAuthDTO) request.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE);
        if (auth == null) {
            // 过滤器已拦截；这里只是防止绕过过滤器直接调用的兜底
            throw new V5aiException(ErrorCode.UNAUTHORIZED, "API Key 无效");
        }
        var agents = new ArrayList<ChatAgent>();
        for (String agentKey : auth.agentKeys()) {
            var agent = resolvePublished(agentKey);
            // 绑定残留但已下线/未发布的 Agent 直接跳过，门户只展示当前可用的
            if (agent != null) {
                agents.add(toChatAgent(agent));
            }
        }
        String ownerName = auth.userId() == null ? null : userService.selectNicknameById(auth.userId());
        return R.ok(new ChatBootstrap(auth.name(), auth.trackingId(), ownerName, agents));
    }

    /**
     * 代读 Agent 头像：仅限「已发布」Agent，且路径上的 agentKey 必须属于当前 Key（过滤器已校验绑定）。
     */
    @GetMapping("/{agentKey}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable("agentKey") String agentKey) {
        var agent = resolvePublished(agentKey);
        Long resourceId = agent == null ? null : resourceIdOf(agent.avatar());
        if (resourceId == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            var content = resourceContentPort.read(resourceId);
            return ResponseEntity.ok()
                    .contentType(parseMediaType(content.mimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(content.bytes());
        } catch (Exception e) {
            log.warn("门户读取 Agent 头像失败：agentKey={} resourceId={} err={}", agentKey, resourceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 解析已发布 Agent；未发布/不存在时返回 null（调用方据此跳过）。
     */
    private AgentDTO resolvePublished(String agentKey) {
        try {
            return publishedAgentResolver.resolve(agentKey);
        } catch (AgentPublishException e) {
            log.debug("门户跳过不可用 Agent：{}（{}）", agentKey, e.getMessage());
            return null;
        }
    }

    private ChatAgent toChatAgent(AgentDTO agent) {
        return new ChatAgent(agent.agentKey(), agent.name(), agent.description(),
                avatarUrlOf(agent), agent.greeting(), parsePresetQuestions(agent.presetQuestions()),
                agent.webSearchEnabled(), modelImageSupportResolver.supportsImageInput(agent.modelId()),
                agent.showCitations());
    }

    /**
     * 头像地址：外部 http(s) 直接可用；应用内资源地址改走门户代读；其余（空/无法识别）返回 null。
     */
    private String avatarUrlOf(AgentDTO agent) {
        String avatar = agent.avatar();
        if (avatar == null || avatar.isBlank()) {
            return null;
        }
        if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
            return avatar;
        }
        return RESOURCE_PATH.matcher(avatar).find()
                ? "/api/v1/agents/auth/" + agent.agentKey() + "/avatar"
                : null;
    }

    /**
     * 从资源访问地址里取出资源 id（{@code /api/admin/resources/{id}/preview}）。
     */
    private Long resourceIdOf(String avatar) {
        if (avatar == null) {
            return null;
        }
        var matcher = RESOURCE_PATH.matcher(avatar);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析快照里的预设问题（JSON 数组字符串）；格式非法时退化为空列表，不影响门户可用性。
     */
    private List<String> parsePresetQuestions(String presetQuestions) {
        if (presetQuestions == null || presetQuestions.isBlank()) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(presetQuestions).toList(String.class);
        } catch (Exception e) {
            log.debug("预设问题解析失败，已忽略：{}", e.getMessage());
            return List.of();
        }
    }

    private MediaType parseMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
