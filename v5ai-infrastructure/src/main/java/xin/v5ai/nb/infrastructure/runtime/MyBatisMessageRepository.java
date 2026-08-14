package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.runtime.Message;
import xin.v5ai.nb.runtime.MessageRepository;
import xin.v5ai.nb.runtime.MessageRole;

import java.util.List;

@Component
public class MyBatisMessageRepository implements MessageRepository {
    private final RuntimeMessageGateway gateway;

    public MyBatisMessageRepository(RuntimeMessageGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(Message message) {
        var entity = new RuntimeMessageEntity();
        entity.setConversationId(message.conversationId());
        entity.setAppKey(message.appKey());
        entity.setRole(message.role().name());
        entity.setContent(message.content());
        gateway.insertMessage(entity);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return gateway.selectMessagesByConversationId(conversationId).stream()
                .map(entity -> new Message(entity.getConversationId(), entity.getAppKey(),
                        MessageRole.valueOf(entity.getRole()), entity.getContent()))
                .toList();
    }
}
