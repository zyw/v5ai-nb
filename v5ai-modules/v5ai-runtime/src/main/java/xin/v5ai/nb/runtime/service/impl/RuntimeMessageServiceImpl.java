package xin.v5ai.nb.runtime.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.mapper.RuntimeMessageMapper;
import xin.v5ai.nb.runtime.service.IRuntimeMessageService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeMessageServiceImpl implements IRuntimeMessageService, MessageService {

    private final RuntimeMessageMapper baseMapper;

    @Override
    public boolean save(SessionMessage message) {
        var entity = new RuntimeMessage();
        entity.setConversationId(message.conversationId());
        entity.setAgentKey(message.agentKey());
        entity.setRole(message.role().name());
        entity.setContent(message.content());
        var result = baseMapper.insert(entity) > 0;
        if (!result) {
            log.info("Message saved failed: {}", message);
        }
        log.info("Message saved successfully: {}", message);
        return result;
    }

    @Override
    public List<SessionMessage> findByConversationId(String conversationId) {
        var result = baseMapper.selectVoList(
                QueryBuilder.lambda(RuntimeMessage.class)
                        .eq(RuntimeMessage::getConversationId, conversationId)
                        .build());

        if (CollUtil.isEmpty(result)) {
            log.info("No messages found for conversationId: {}", conversationId);
            return CollUtil.newArrayList();
        }
        log.info("Found {} messages for conversationId: {}", result.size(), conversationId);
        return result.stream().map(entity -> new SessionMessage(entity.getConversationId(), entity.getAgentKey(),
                MessageRole.valueOf(entity.getRole()), entity.getContent())).toList();
    }
}
