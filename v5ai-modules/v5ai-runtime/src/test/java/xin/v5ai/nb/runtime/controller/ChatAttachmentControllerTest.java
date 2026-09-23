package xin.v5ai.nb.runtime.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.RateLimiter;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;
import xin.v5ai.nb.runtime.core.config.properties.ChatAttachmentProperties;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;

import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户附件上传 / 读取的单测：类型按文件头判定（不采信扩展名）、体积上限、按 Key 限流，
 * 以及读取端的推导式鉴权（本 Key 没引用过就 404，不泄露资源存在性）。
 */
class ChatAttachmentControllerTest {

    private static final long API_KEY_ID = 1L;
    private static final long OWNER_USER_ID = 7L;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 1, 2};

    private ResourceContentPort resourcePort;
    private MessageAttachmentService attachmentService;
    private RateLimiter rateLimiter;
    private ChatAttachmentProperties properties;
    private ChatAttachmentController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resourcePort = mock(ResourceContentPort.class);
        attachmentService = mock(MessageAttachmentService.class);
        rateLimiter = mock(RateLimiter.class);
        properties = new ChatAttachmentProperties();
        controller = new ChatAttachmentController(resourcePort, attachmentService, rateLimiter, properties);

        request = mock(HttpServletRequest.class);
        when(request.getAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE))
                .thenReturn(new ApiKeysAuthDTO(API_KEY_ID, OWNER_USER_ID, "门户Key", "track-1", true, Set.of("a1")));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
    }

    @Test
    void validPngIsStoredAsAttachmentOwnedByTheKeyUser() {
        when(resourcePort.save(anyString(), any(), anyString(), anyString(), any(), anyLong())).thenReturn(42L);

        var result = controller.upload(upload("logo.png", PNG), request).getData();

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.accessUrl()).isEqualTo("/api/v1/agents/attachments/42");
        // bizType 由服务端强制为 ATTACHMENT；createdBy 取 Key 的归属用户（API Key 路径没有 Sa-Token 会话）
        verify(resourcePort).save(eq("logo.png"), any(), eq("image/png"), eq("ATTACHMENT"), any(), eq(OWNER_USER_ID));
    }

    @Test
    void callerSuppliedBizTypeIsIgnoredAndForcedToAttachment() {
        when(resourcePort.save(anyString(), any(), anyString(), anyString(), any(), anyLong())).thenReturn(42L);

        controller.upload(new ChatAttachmentController.AttachmentUploadBo(
                "logo.png", null, base64(PNG), "AVATAR", 99L), request);

        verify(resourcePort).save(anyString(), any(), anyString(), eq("ATTACHMENT"), eq(99L), eq(OWNER_USER_ID));
    }

    @Test
    void textContentDisguisedAsPngIsRejected() {
        assertThat(failure(upload("x.png", "hello, definitely not a png".getBytes()), request).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(resourcePort, never()).save(anyString(), any(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void supportedImageHeadersAreAccepted() {
        when(resourcePort.save(anyString(), any(), anyString(), anyString(), any(), anyLong())).thenReturn(1L);

        controller.upload(upload("a.jpg", JPEG), request);
        controller.upload(upload("a.webp", WEBP), request);

        verify(resourcePort).save(anyString(), any(), eq("image/jpeg"), anyString(), any(), anyLong());
        verify(resourcePort).save(anyString(), any(), eq("image/webp"), anyString(), any(), anyLong());
    }

    @Test
    void contentLargerThanTheLimitIsRejected() {
        byte[] tooLarge = new byte[(int) properties.getMaxFileSizeBytes() + 1];
        System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);

        assertThat(failure(upload("big.png", tooLarge), request).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(resourcePort, never()).save(anyString(), any(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void declaredFileSizeAboveTheLimitIsRejectedBeforeDecoding() {
        var bo = new ChatAttachmentController.AttachmentUploadBo(
                "big.png", properties.getMaxFileSizeBytes() + 1, base64(PNG), null, null);

        assertThat(failure(bo, request).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void invalidBase64AndBlankContentAreRejected() {
        assertThat(failure(upload("a.png", "%%%".getBytes()), request).code())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(failure(new ChatAttachmentController.AttachmentUploadBo(
                "a.png", null, "  ", null, null), request).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void blankOriginalNameIsRejected() {
        assertThat(failure(new ChatAttachmentController.AttachmentUploadBo(
                "  ", null, base64(PNG), null, null), request).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void missingApiKeyContextIsUnauthorized() {
        var anonymous = mock(HttpServletRequest.class);

        assertThat(failure(upload("a.png", PNG), anonymous).code()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void uploadBeyondThePerKeyRateLimitIsRejected() {
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(false);

        assertThat(failure(upload("a.png", PNG), request).code()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        verify(resourcePort, never()).save(anyString(), any(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void attachmentIsReadableWhenOwnKeyReferencedIt() {
        when(attachmentService.referencedByApiKey(42L, API_KEY_ID)).thenReturn(true);
        when(resourcePort.read(42L)).thenReturn(new ResourceContentPort.Content(PNG, "image/png", "logo.png"));

        var response = controller.read(42L, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isEqualTo(PNG);
    }

    @Test
    void attachmentReadByAnotherKeyIsNotFound() {
        when(attachmentService.referencedByApiKey(42L, API_KEY_ID)).thenReturn(false);

        assertThat(controller.read(42L, request).getStatusCode().value()).isEqualTo(404);
        verify(resourcePort, never()).read(anyLong());
    }

    @Test
    void attachmentWhoseBytesAreGoneIsNotFound() {
        when(attachmentService.referencedByApiKey(42L, API_KEY_ID)).thenReturn(true);
        when(resourcePort.read(42L)).thenThrow(new IllegalStateException("资源不存在: 42"));

        assertThat(controller.read(42L, request).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void attachmentReadWithoutApiKeyContextIsUnauthorized() {
        var anonymous = mock(HttpServletRequest.class);

        assertThat(catchThrowableOfType(() -> controller.read(42L, anonymous), V5aiException.class).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private ChatAttachmentController.AttachmentUploadBo upload(String name, byte[] bytes) {
        return new ChatAttachmentController.AttachmentUploadBo(name, (long) bytes.length, base64(bytes), null, null);
    }

    private V5aiException failure(ChatAttachmentController.AttachmentUploadBo bo, HttpServletRequest req) {
        return catchThrowableOfType(() -> controller.upload(bo, req), V5aiException.class);
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
