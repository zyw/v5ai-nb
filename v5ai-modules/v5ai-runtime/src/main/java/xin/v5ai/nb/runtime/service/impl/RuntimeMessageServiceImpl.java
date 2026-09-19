package xin.v5ai.nb.runtime.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.RunUsage;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.domain.vo.RuntimeMessageVo;
import xin.v5ai.nb.runtime.mapper.RuntimeMessageMapper;
import xin.v5ai.nb.runtime.service.IRuntimeMessageService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeMessageServiceImpl implements IRuntimeMessageService, MessageService {

    private final RuntimeMessageMapper baseMapper;
    private final MessageAttachmentService messageAttachmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SessionMessage message) {
        var entity = new RuntimeMessage();
        entity.setConversationId(message.conversationId());
        entity.setAgentKey(message.agentKey());
        entity.setRole(message.role().name());
        entity.setContent(message.content());
        // 用量跟着回答一起落库：事件流里的数字只活在那一次 SSE 里，历史接口要能重新给出它
        var usage = message.usage();
        if (usage != null) {
            entity.setPromptTokens((int) usage.promptTokens());
            entity.setCompletionTokens((int) usage.completionTokens());
            entity.setDurationMs(usage.durationMsAsInt());
        }
        if (baseMapper.insert(entity) <= 0) {
            log.info("Message saved failed: {}", message);
            return null;
        }
        // 附件与消息同事务：消息落库成功才挂附件，且插入后已回填自增主键
        messageAttachmentService.saveAll(entity.getId(), message.attachments());
        log.info("Message saved successfully: {}", message);
        return entity.getId();
    }

    @Override
    public List<SessionMessage> findByConversationId(String conversationId) {
        // 稳定顺序：created_at 只到微秒且同一次运行的「提问」与「回答」可能同刻写入，
        // 再用自增主键 id 兜底，保证回放顺序在任何并发写入下都确定（见 v5ai-nb#6 验收标准 6）。
        var result = baseMapper.selectVoList(
                QueryBuilder.lambda(RuntimeMessage.class)
                        .eq(RuntimeMessage::getConversationId, conversationId)
                        // 作废的消息不参与展示与记忆回放（历史接口、resume、withMemory 都走这里）
                        .isNull(RuntimeMessage::getSupersededAt)
                        .orderByAsc(RuntimeMessage::getCreatedAt, RuntimeMessage::getId)
                        .build());

        if (CollUtil.isEmpty(result)) {
            log.info("No messages found for conversationId: {}", conversationId);
            return CollUtil.newArrayList();
        }
        log.info("Found {} messages for conversationId: {}", result.size(), conversationId);
        return withAttachments(result);
    }

    @Override
    public Optional<SessionMessage> findActiveMessage(Long messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        var row = baseMapper.selectOne(
                QueryBuilder.lambda(RuntimeMessage.class)
                        .eq(RuntimeMessage::getId, messageId)
                        .isNull(RuntimeMessage::getSupersededAt)
                        .build());
        if (row == null) {
            return Optional.empty();
        }
        var attachments = messageAttachmentService.findByMessageIds(List.of(row.getId()))
                .getOrDefault(row.getId(), List.of());
        return Optional.of(new SessionMessage(row.getConversationId(), row.getAgentKey(),
                MessageRole.valueOf(row.getRole()), row.getContent(), attachments)
                .withUsage(RunUsage.fromNullable(row.getPromptTokens(), row.getCompletionTokens(),
                        row.getDurationMs()))
                .withMessageId(row.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int supersedeAfter(String conversationId, Long messageId) {
        if (messageId == null) {
            return 0;
        }
        // 只标记、不删除：作废的消息保留数据（审计完整、可反悔、附件引用不断），
        // 只是不再参与展示与记忆回放。锚点消息本身保持有效——重新生成复用它。
        return baseMapper.update(null, new LambdaUpdateWrapper<RuntimeMessage>()
                .eq(RuntimeMessage::getConversationId, conversationId)
                .gt(RuntimeMessage::getId, messageId)
                .isNull(RuntimeMessage::getSupersededAt)
                .set(RuntimeMessage::getSupersededAt, OffsetDateTime.now()));
    }

    /**
     * 行 → 领域消息：批量取附件（历史回放时消息数不少，逐条查会成为 N+1），
     * 用量三列齐备才还原（用户消息与本迁移之前的回答都是 NULL）。
     */
    private List<SessionMessage> withAttachments(List<RuntimeMessageVo> rows) {
        Map<Long, List<AttachmentRef>> attachments = messageAttachmentService.findByMessageIds(
                rows.stream().map(RuntimeMessageVo::getId).toList());
        return rows.stream().map(row -> new SessionMessage(row.getConversationId(), row.getAgentKey(),
                MessageRole.valueOf(row.getRole()), row.getContent(),
                attachments.getOrDefault(row.getId(), List.of()))
                .withUsage(RunUsage.fromNullable(row.getPromptTokens(), row.getCompletionTokens(),
                        row.getDurationMs()))
                // 主键要交给前端：重新生成以该轮提问的消息 id 为锚点
                .withMessageId(row.getId())).toList();
    }
}
