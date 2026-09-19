package xin.v5ai.nb.runtime.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.runtime.core.domain.ConversationSummaryDTO;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.runtime.mapper.ConversationSummaryMapper;

import java.util.Optional;

/**
 * 会话摘要仓储实现：所有「谁更新」的判定都交给数据库（见 {@code ConversationSummaryMapper.xml}），
 * 这样异步生成的两次摘要并发时也不会互相覆盖出旧结果。
 */
@Service
@RequiredArgsConstructor
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private final ConversationSummaryMapper baseMapper;

    @Override
    public Optional<ConversationSummaryDTO> find(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        var vo = baseMapper.selectVoById(conversationId);
        if (vo == null || vo.getSummary() == null || vo.getSummary().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ConversationSummaryDTO(vo.getConversationId(), vo.getSummary(),
                vo.getCoveredUntilMessageId() == null ? 0L : vo.getCoveredUntilMessageId(),
                vo.getCoveredMessages() == null ? 0 : vo.getCoveredMessages(),
                vo.getModelId(), vo.getUpdatedAt()));
    }

    @Override
    public boolean upsertIfNewer(String conversationId, String summary, long coveredUntilMessageId,
                                 int coveredMessages, Long modelId) {
        return baseMapper.upsertIfNewer(conversationId, summary, coveredUntilMessageId, coveredMessages,
                modelId) > 0;
    }

    @Override
    public boolean invalidateIfCoveringAfter(String conversationId, Long messageId) {
        if (conversationId == null || messageId == null) {
            return false;
        }
        return baseMapper.deleteIfCoveringAfter(conversationId, messageId) > 0;
    }

    @Override
    public int deleteByAgentKey(String agentKey) {
        return baseMapper.deleteByAgentKey(agentKey);
    }
}
