package xin.v5ai.nb.runtime.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.RunUsage;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.rag.CitationPayload;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.domain.vo.RuntimeMessageVo;
import xin.v5ai.nb.runtime.mapper.RuntimeMessageMapper;
import xin.v5ai.nb.runtime.service.IRuntimeMessageService;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeMessageServiceImpl implements IRuntimeMessageService, MessageService {

    /**
     * 不进热路径取数的列：思考与引用都可能很长（引用实测约 8.5KB/条消息），而记忆窗口/摘要
     * 取数**每一轮都要跑**，读了也只会被丢掉（两者都不回放给模型，见 docs/adr/0007 与 ADR-0009）。
     *
     * <p>用「排除表 + 谓词」而不是手写列清单：新加列默认仍会被投影，只有真正的大列
     * 才需要显式列在这里。</p>
     */
    private static final Set<String> HOT_PATH_EXCLUDED_COLUMNS = Set.of("reasoning", "metadata");

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
        // 思考跟着回答一起落库（仅助手消息有值）：刷新或重进会话后门户仍能回看
        entity.setReasoning(message.reasoning());
        // 引用同样跟着回答落库（仅助手消息有值）：存的是当时 SSE 载荷的快照，无引用时不写这一列
        // （NULL = 这一轮没有引用）。见 docs/adr/0009-citations-persisted-on-assistant-message.md
        entity.setMetadata(CitationPayload.toMetadata(message.citations()));
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
        // 附件与消息同事务：消息落库成功才挂附件，且插入后主键已回填
        // （雪花号，不是库里的 BIGSERIAL 序列值——对外按字符串收发，见 RuntimeMessage#id）
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

    /**
     * 最近 N 条：SQL 侧按 (created_at, id) 倒序取，再反转回时间正序——顺序口径与
     * {@link #findByConversationId(String)} 完全一致，只是多了一个 LIMIT。
     */
    @Override
    public List<SessionMessage> findRecentByConversationId(String conversationId, int limit) {
        if (limit <= 0) {
            return findByConversationId(conversationId);
        }
        var wrapper = QueryBuilder.lambda(RuntimeMessage.class)
                .eq(RuntimeMessage::getConversationId, conversationId)
                .isNull(RuntimeMessage::getSupersededAt)
                .orderByDesc(RuntimeMessage::getCreatedAt, RuntimeMessage::getId)
                .last("LIMIT " + limit)
                .build();
        // 记忆窗口是每轮的热路径：剔除大列（思考），历史接口才按需读全文
        excludeHotPathColumns(wrapper);
        var result = baseMapper.selectVoList(wrapper);
        if (CollUtil.isEmpty(result)) {
            return CollUtil.newArrayList();
        }
        // 倒序取、正序回：窗口裁剪与执行器都按时间正序消费
        Collections.reverse(result);
        return withAttachments(result);
    }

    /**
     * 区间取数：SQL 侧按 (created_at, id) 倒序取最近的 limit 条，再反转回时间正序——
     * 与 {@link #findRecentByConversationId(String, int)} 同一手法，只是多了上下界。
     */
    @Override
    public List<SessionMessage> findActiveBetween(String conversationId, Long afterMessageId,
                                                  Long beforeMessageId, int limit) {
        // 上界是一条消息 id（历史窗口内最早那条）：没有它就没有「区间」可言
        if (conversationId == null || conversationId.isBlank() || beforeMessageId == null) {
            return List.of();
        }
        var query = QueryBuilder.lambda(RuntimeMessage.class)
                .eq(RuntimeMessage::getConversationId, conversationId)
                .isNull(RuntimeMessage::getSupersededAt)
                .gt(afterMessageId != null, RuntimeMessage::getId, afterMessageId)
                .lt(RuntimeMessage::getId, beforeMessageId)
                .orderByDesc(RuntimeMessage::getCreatedAt, RuntimeMessage::getId);
        if (limit > 0) {
            query.last("LIMIT " + limit);
        }
        // 摘要是按水位批量取数的后台路径，同样用不上思考：剔除大列
        var wrapper = query.build();
        excludeHotPathColumns(wrapper);
        var result = baseMapper.selectVoList(wrapper);
        if (CollUtil.isEmpty(result)) {
            return CollUtil.newArrayList();
        }
        Collections.reverse(result);
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
                .withMessageId(row.getId())
                .withReasoning(row.getReasoning())
                .withCitations(CitationPayload.parseMetadata(row.getMetadata())));
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
                .withMessageId(row.getId())
                // 思考与引用都只有历史接口读得到（窗口/摘要查询不投影这两列，读回即 null）
                .withReasoning(row.getReasoning())
                .withCitations(CitationPayload.parseMetadata(row.getMetadata()))).toList();
    }

    /**
     * 把热路径用不上的大列从投影里去掉（见 {@link #HOT_PATH_EXCLUDED_COLUMNS}）。
     */
    private static void excludeHotPathColumns(LambdaQueryWrapper<RuntimeMessage> wrapper) {
        wrapper.select(RuntimeMessage.class,
                info -> !HOT_PATH_EXCLUDED_COLUMNS.contains(info.getColumn()));
    }
}
