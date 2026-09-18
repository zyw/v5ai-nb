package xin.v5ai.nb.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.RuntimeMessageAttachment;
import xin.v5ai.nb.runtime.domain.vo.RuntimeMessageAttachmentVo;

@Mapper
public interface RuntimeMessageAttachmentMapper
        extends BaseMapperPlus<RuntimeMessageAttachment, RuntimeMessageAttachmentVo> {

    /**
     * 推导式鉴权（ADR 0006）：该资源是否被这把 API Key 的某条消息引用过。
     *
     * <p>门户读取附件时不查 {@code plm_resource.created_by}（那把 Key 的归属用户可能被换绑），
     * 而是顺着 {@code v5ai_message_attachment → v5ai_message → v5ai_conversation.api_key_id}
     * 确认「这个资源确实是本 Key 自己发出去的」。查不通一律按不存在处理（404，不泄露存在性）。</p>
     *
     * @param resourceId 资源标识
     * @param apiKeyId   发起读取的 API Key
     * @return 被引用过返回 true
     */
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM v5ai_message_attachment a
                         JOIN v5ai_message m ON m.id = a.message_id
                         JOIN v5ai_conversation c ON c.id = m.conversation_id
                WHERE a.resource_id = #{resourceId}
                  AND c.api_key_id = #{apiKeyId}
            )
            """)
    boolean existsReferencedByApiKey(@Param("resourceId") Long resourceId,
                                     @Param("apiKeyId") Long apiKeyId);
}
