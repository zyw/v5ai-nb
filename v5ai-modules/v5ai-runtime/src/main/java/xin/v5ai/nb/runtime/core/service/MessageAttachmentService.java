package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 消息附件仓储端口：消息与附件（图片）的关联关系。
 *
 * <p>文件字节不在这里——本端口只回答「哪条消息带了哪些资源」，
 * 读取字节走 {@code ResourceContentPort}。</p>
 */
public interface MessageAttachmentService {

    /**
     * 保存一条消息携带的全部附件（按列表顺序落 ordinal）。
     *
     * @param messageId   消息主键（v5ai_message.id）
     * @param attachments 附件引用列表；空列表时不做任何写入
     */
    void saveAll(Long messageId, List<AttachmentRef> attachments);

    /**
     * 按消息主键批量取附件，供历史回放与消息查询使用。
     *
     * @param messageIds 消息主键集合
     * @return 消息主键 -> 附件列表（按 ordinal 升序）；无附件的消息不出现在结果里
     */
    Map<Long, List<AttachmentRef>> findByMessageIds(Collection<Long> messageIds);

    /**
     * 推导式鉴权（ADR 0006）：该资源是否被这把 API Key 的某条消息引用过。
     *
     * @param resourceId 资源标识
     * @param apiKeyId   发起读取的 API Key；为 null（调试入口）时一律返回 false
     * @return 被引用过返回 true
     */
    boolean referencedByApiKey(Long resourceId, Long apiKeyId);
}
