package xin.v5ai.nb.common.agentscope.core.domain;

/**
 * 消息携带的附件引用（不可变 record）。
 *
 * <p>只承载「哪条消息带了哪个资源、是哪种附件」，文件字节本身在通用资源存储
 * （{@code plm_resource}，{@code biz_type=ATTACHMENT}）里，运行时按需读取。</p>
 *
 * @param type       附件类型；当前平台只支持 {@link #TYPE_IMAGE}
 * @param resourceId 资源存储（{@code plm_resource.id}）中的资源标识
 */
public record AttachmentRef(String type, Long resourceId) {

    /**
     * 附件类型：图片。当前平台只支持这一种（见 CONTEXT.md「附件」）。
     */
    public static final String TYPE_IMAGE = "IMAGE";

    /**
     * 构造一个图片附件引用。
     */
    public static AttachmentRef image(Long resourceId) {
        return new AttachmentRef(TYPE_IMAGE, resourceId);
    }

    /**
     * 是否为图片附件（类型大小写不敏感，容忍调用方写 image）。
     */
    public boolean isImage() {
        return type != null && TYPE_IMAGE.equalsIgnoreCase(type.trim());
    }
}
