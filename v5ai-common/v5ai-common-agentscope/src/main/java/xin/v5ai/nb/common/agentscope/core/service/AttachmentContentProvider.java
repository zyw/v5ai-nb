package xin.v5ai.nb.common.agentscope.core.service;

/**
 * 附件内容读取端口：运行时按资源 id 取回附件的字节，用于构造多模态消息。
 *
 * <p>运行时（common-agentscope）不依赖资源存储模块，所以这里只声明读能力；
 * 实现由运行时模块委托通用资源存储（{@code plm_resource}）提供。
 * 未装配该 Bean 时运行时会忽略消息上的附件（退化为纯文本）。</p>
 */
public interface AttachmentContentProvider {

    /**
     * 读取附件内容。
     *
     * @param resourceId 资源标识（{@code plm_resource.id}）
     * @return 文件字节与 MIME 类型
     * @throws RuntimeException 资源不存在或读取失败时抛出，调用方按「该图不可用」降级处理
     */
    AttachmentContent read(Long resourceId);

    /**
     * 附件内容。
     *
     * @param bytes    文件字节
     * @param mimeType MIME 类型（如 image/png）
     */
    record AttachmentContent(byte[] bytes, String mimeType) {
    }
}
