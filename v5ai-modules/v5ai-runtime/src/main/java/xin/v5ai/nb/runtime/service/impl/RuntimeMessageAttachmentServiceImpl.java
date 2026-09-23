package xin.v5ai.nb.runtime.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;
import xin.v5ai.nb.runtime.domain.RuntimeMessageAttachment;
import xin.v5ai.nb.runtime.mapper.RuntimeMessageAttachmentMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link MessageAttachmentService} 的 MyBatis-Plus 实现。
 */
@Service
@RequiredArgsConstructor
public class RuntimeMessageAttachmentServiceImpl implements MessageAttachmentService {

    private final RuntimeMessageAttachmentMapper baseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(Long messageId, List<AttachmentRef> attachments) {
        if (messageId == null || CollUtil.isEmpty(attachments)) {
            return;
        }
        int ordinal = 0;
        for (AttachmentRef attachment : attachments) {
            if (attachment == null || attachment.resourceId() == null) {
                continue;
            }
            var entity = new RuntimeMessageAttachment();
            entity.setMessageId(messageId);
            entity.setResourceId(attachment.resourceId());
            entity.setType(attachment.type());
            entity.setOrdinal(ordinal++);
            baseMapper.insert(entity);
        }
    }

    @Override
    public Map<Long, List<AttachmentRef>> findByMessageIds(Collection<Long> messageIds) {
        if (CollUtil.isEmpty(messageIds)) {
            return Map.of();
        }
        var rows = baseMapper.selectVoList(
                QueryBuilder.lambda(RuntimeMessageAttachment.class)
                        .in(RuntimeMessageAttachment::getMessageId, messageIds)
                        .orderByAsc(RuntimeMessageAttachment::getMessageId, RuntimeMessageAttachment::getOrdinal)
                        .build());
        if (CollUtil.isEmpty(rows)) {
            return Map.of();
        }
        Map<Long, List<AttachmentRef>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            grouped.computeIfAbsent(row.getMessageId(), key -> new ArrayList<>())
                    .add(new AttachmentRef(row.getType(), row.getResourceId()));
        }
        return grouped;
    }

    @Override
    public boolean referencedByApiKey(Long resourceId, Long apiKeyId) {
        if (resourceId == null || apiKeyId == null) {
            return false;
        }
        return baseMapper.existsReferencedByApiKey(resourceId, apiKeyId);
    }
}
