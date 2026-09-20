package xin.v5ai.nb.runtime.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.RateLimiter;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;
import xin.v5ai.nb.runtime.core.config.properties.ChatAttachmentProperties;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;

import java.util.Base64;

/**
 * 对话门户的附件端点（{@code /api/v1/agents/resource/**}、{@code /api/v1/agents/attachments/**}）。
 *
 * <p>都在 API Key 守卫下（{@code AgentApiKeysServletFilter}）；这两个族不带 agentKey，
 * 因此过滤器只验 Key、不校验绑定也不计模型配额——附件是 Key 维度的资源，与具体 Agent 无关。</p>
 *
 * <ul>
 *   <li>{@code POST /resource/upload}：JSON body 携带 base64 内容（门户无法用 multipart —— 它只持有 Key，
 *       而管理端上传入口要 Sa-Token 登录态）。服务端强制 {@code bizType=ATTACHMENT}，
 *       按文件头字面量校验类型、限制体积、按 Key 限流；</li>
 *   <li>{@code GET /attachments/{resourceId}}：inline 返回图片字节。鉴权是**推导式**的：
 *       只有「本 Key 的某条消息引用过该资源」才可读，否则一律 404（不泄露资源是否存在）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agents")
public class ChatAttachmentController {

    /**
     * 附件资源的业务类型：服务端强制，不接受调用方指定。
     */
    private static final String BIZ_TYPE_ATTACHMENT = "ATTACHMENT";

    /**
     * 附件资源的访问 URL 前缀（门户侧读取入口）。
     */
    private static final String ATTACHMENT_URL_PREFIX = "/api/v1/agents/attachments/";

    /**
     * 上传限流的维度前缀：按 API Key 计数（同一把 Key 的并发调用方共享额度）。
     */
    private static final String UPLOAD_RATE_KEY_PREFIX = "portal:attachment:upload:";

    private final ResourceContentPort resourceContentPort;
    private final MessageAttachmentService messageAttachmentService;
    private final RateLimiter rateLimiter;
    private final ChatAttachmentProperties properties;

    /**
     * 上传附件（图片）。
     *
     * @param bo      {@code {originalName, fileSize, content(base64), bizType?, bizId?}}；
     *                {@code bizType} 恒被强制为 {@code ATTACHMENT}
     * @param request 请求（用于取过滤器放进去的 API Key 鉴权结果）
     * @return 资源 id 与其门户访问地址
     */
    @PostMapping("/resource/upload")
    public R<AttachmentUploadResult> upload(@RequestBody AttachmentUploadBo bo, HttpServletRequest request) {
        var auth = requireAuth(request);
        if (!rateLimiter.tryAcquire(UPLOAD_RATE_KEY_PREFIX + auth.apiKeyId(), properties.getUploadsPerMinute())) {
            throw new V5aiException(ErrorCode.TOO_MANY_REQUESTS, "附件上传过于频繁，请稍后再试");
        }
        var originalName = bo.originalName() == null ? "" : bo.originalName().trim();
        if (originalName.isEmpty()) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "文件名不能为空");
        }
        // 先按调用方申报的体积快速拒绝，省掉一次大对象解码
        if (bo.fileSize() != null && bo.fileSize() > properties.getMaxFileSizeBytes()) {
            throw tooLarge();
        }
        byte[] bytes = decode(bo.content());
        if (bytes.length > properties.getMaxFileSizeBytes()) {
            throw tooLarge();
        }
        String mimeType = detectImageMimeType(bytes);
        if (mimeType == null) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "附件仅支持 PNG / JPEG / WebP 图片");
        }
        Long id = resourceContentPort.save(originalName, bytes, mimeType, BIZ_TYPE_ATTACHMENT,
                bo.bizId(), auth.userId());
        log.info("门户上传附件成功：apiKeyId={} resourceId={} bytes={} mime={}",
                auth.apiKeyId(), id, bytes.length, mimeType);
        return R.ok(new AttachmentUploadResult(id, ATTACHMENT_URL_PREFIX + id));
    }

    /**
     * 读取附件字节（inline）。不可读时统一 404——不区分「不存在」与「不属于本 Key」，
     * 避免用状态码探测别人的资源是否存在。
     */
    @GetMapping("/attachments/{resourceId}")
    public ResponseEntity<byte[]> read(@PathVariable("resourceId") Long resourceId,
                                       HttpServletRequest request) {
        var auth = requireAuth(request);
        if (!messageAttachmentService.referencedByApiKey(resourceId, auth.apiKeyId())) {
            return ResponseEntity.notFound().build();
        }
        try {
            var content = resourceContentPort.read(resourceId);
            return ResponseEntity.ok()
                    .contentType(parseMediaType(content.mimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(content.bytes());
        } catch (Exception e) {
            // 引用了但字节已不在（资源被清理）：对调用方同样按不存在处理
            log.warn("门户读取附件失败：resourceId={} err={}", resourceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 取过滤器写入请求属性的 API Key 鉴权结果；缺失说明请求绕过了过滤器，按未鉴权拒绝。
     */
    private ApiKeysAuthDTO requireAuth(HttpServletRequest request) {
        var auth = (ApiKeysAuthDTO) request.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE);
        if (auth == null) {
            throw new V5aiException(ErrorCode.UNAUTHORIZED, "API Key 无效");
        }
        return auth;
    }

    private V5aiException tooLarge() {
        return new V5aiException(ErrorCode.INVALID_ARGUMENT,
                "附件超过单张体积上限 " + (properties.getMaxFileSizeBytes() / 1024 / 1024) + "MB");
    }

    /**
     * 解码 base64 内容；内容缺失或不是合法 base64 时按参数错误拒绝。
     */
    private byte[] decode(String content) {
        if (content == null || content.isBlank()) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "附件内容不能为空");
        }
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "附件内容不是合法的 base64");
        }
    }

    /**
     * 按文件头（magic bytes）判定图片类型。
     *
     * <p>不采信调用方给的扩展名或 MIME：一个内容是文本的 {@code x.png} 必须被拒
     * （见 v5ai-nb#6 验收标准 1、Q11）。</p>
     *
     * @return 判定出的 MIME 类型；不是受支持的图片时返回 null
     */
    private String detectImageMimeType(byte[] bytes) {
        if (startsWith(bytes, new int[]{0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF})) {
            return "image/jpeg";
        }
        // WebP: "RIFF" ....(4 字节长度).... "WEBP"
        if (startsWith(bytes, new int[]{'R', 'I', 'F', 'F'}) && matchesAt(bytes, 8, new int[]{'W', 'E', 'B', 'P'})) {
            return "image/webp";
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, int[] signature) {
        return matchesAt(bytes, 0, signature);
    }

    private boolean matchesAt(byte[] bytes, int offset, int[] signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private MediaType parseMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 附件上传请求体（不可变 record）。
     *
     * @param originalName 原始文件名（仅作展示与元数据，类型判定不看它）
     * @param fileSize     调用方申报的字节数；仅用于解码前的快速拒绝，实际以解码结果为准
     * @param content      文件内容的 base64（不含 data URI 前缀）
     * @param bizType      忽略——服务端恒定写 {@code ATTACHMENT}，保留字段只为契约稳定
     * @param bizId        关联业务 ID，可为空
     */
    public record AttachmentUploadBo(String originalName, Long fileSize, String content,
                                     String bizType, Long bizId) {
    }

    /**
     * 附件上传结果：资源 id 与门户读取地址。
     *
     * @param id        资源 id（随消息提交时用）
     * @param accessUrl 门户读取地址
     */
    public record AttachmentUploadResult(Long id, String accessUrl) {
    }
}
