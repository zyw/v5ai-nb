package xin.v5ai.nb.runtime.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.domain.vo.RuntimeConversationPreviewVo;
import xin.v5ai.nb.runtime.mapper.RuntimeMessageMapper;
import xin.v5ai.nb.runtime.service.IRuntimeMessageService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeMessageServiceImpl implements IRuntimeMessageService, MessageService {

    private final RuntimeMessageMapper baseMapper;
    private final MessageAttachmentService messageAttachmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SessionMessage message) {
        var entity = new RuntimeMessage();
        entity.setConversationId(message.conversationId());
        entity.setAgentKey(message.agentKey());
        entity.setRole(message.role().name());
        entity.setContent(message.content());
        var result = baseMapper.insert(entity) > 0;
        if (!result) {
            log.info("Message saved failed: {}", message);
            return false;
        }
        // 附件与消息同事务：消息落库成功才挂附件，且插入后已回填自增主键
        messageAttachmentService.saveAll(entity.getId(), message.attachments());
        log.info("Message saved successfully: {}", message);
        return true;
    }

    @Override
    public List<SessionMessage> findByConversationId(String conversationId) {
        // 稳定顺序：created_at 只到微秒且同一次运行的「提问」与「回答」可能同刻写入，
        // 再用自增主键 id 兜底，保证回放顺序在任何并发写入下都确定（见 v5ai-nb#6 验收标准 6）。
        var result = baseMapper.selectVoList(
                QueryBuilder.lambda(RuntimeMessage.class)
                        .eq(RuntimeMessage::getConversationId, conversationId)
                        .orderByAsc(RuntimeMessage::getCreatedAt, RuntimeMessage::getId)
                        .build());

        if (CollUtil.isEmpty(result)) {
            log.info("No messages found for conversationId: {}", conversationId);
            return CollUtil.newArrayList();
        }
        log.info("Found {} messages for conversationId: {}", result.size(), conversationId);
        // 附件按消息批量取回，避免每条消息各查一次（历史回放时消息数不少）
        Map<Long, List<AttachmentRef>> attachments = messageAttachmentService.findByMessageIds(
                        result.stream().map(entity -> entity.getId()).toList());
        return result.stream().map(entity -> new SessionMessage(entity.getConversationId(), entity.getAgentKey(),
                MessageRole.valueOf(entity.getRole()), entity.getContent(),
                attachments.getOrDefault(entity.getId(), List.of()))).toList();
    }

    @Override
    public Map<String, String> findFirstUserMessageByConversationIds(Collection<String> conversationIds) {
        if (CollUtil.isEmpty(conversationIds)) {
            return Map.of();
        }
        return baseMapper.selectFirstUserMessages(conversationIds).stream()
                .filter(row -> row.getConversationId() != null)
                .collect(Collectors.toMap(RuntimeConversationPreviewVo::getConversationId,
                        row -> row.getContent() == null ? "" : row.getContent(),
                        (first, ignored) -> first));
    }
}
