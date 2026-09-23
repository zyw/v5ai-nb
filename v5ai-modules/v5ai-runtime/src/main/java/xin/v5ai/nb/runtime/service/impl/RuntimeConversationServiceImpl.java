package xin.v5ai.nb.runtime.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.runtime.core.utils.ConversationNaming;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.domain.RuntimeConversation;
import xin.v5ai.nb.runtime.mapper.RuntimeConversationMapper;
import xin.v5ai.nb.runtime.service.IRuntimeConversationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link ConversationService} 的 MyBatis-Plus 实现。
 *
 * <p>归属判定以 {@code api_key_id} 为唯一依据（ADR 0006）；{@code user_id} 只写不判。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeConversationServiceImpl implements IRuntimeConversationService, ConversationService {

    private final RuntimeConversationMapper baseMapper;

    @Override
    public boolean ensureConversation(ConversationDTO conversation) {
        var existing = baseMapper.selectById(conversation.conversationId());
        if (existing == null) {
            var entity = new RuntimeConversation();
            entity.setId(conversation.conversationId());
            entity.setAgentKey(conversation.agentKey());
            entity.setApiKeyId(conversation.apiKeyId());
            entity.setUserId(conversation.userId());
            // 名称只在这里写一次：首条提问的兜底名 + 来源标记。
            // 门户会话列表因此只读本表（否则要对每个会话回查一次 v5ai_message 取预览）。
            if (conversation.name() != null) {
                entity.setName(conversation.name());
                entity.setNameSource(ConversationNaming.SOURCE_AUTO);
            }
            baseMapper.insert(entity);
            return true;
        }
        // 归属断言：门户侧的会话只属于创建它的那把 Key。无主的调试会话同样不可被 Key 认领，
        // 否则「猜到调试会话 ID」就等于拿到它的全部历史。
        if (conversation.apiKeyId() != null && !conversation.apiKeyId().equals(existing.getApiKeyId())) {
            throw notFound(conversation.conversationId());
        }
        // 归档是收尾态：数据保留，但禁止继续对话（见 CONTEXT.md「归档」）
        if (existing.getArchivedAt() != null) {
            throw new V5aiException(ErrorCode.CONFLICT, "会话已归档，请先取消归档再继续对话");
        }
        touch(existing.getId());
        return false;
    }

    /**
     * 把会话标记为「刚刚活跃」：列表按 {@code updated_at} 倒序，没有这一步，新建的会话会永远排在最前，
     * 而最近真正聊过的会话沉到列表底部。
     *
     * <p>时间显式写入而不是依赖自动填充：只带 id 的更新若不带上 {@code updated_at}，
     * 生成出来的 SET 子句可能是空的。</p>
     */
    private void touch(String conversationId) {
        var touch = new RuntimeConversation();
        touch.setId(conversationId);
        touch.setUpdatedAt(OffsetDateTime.now());
        baseMapper.updateById(touch);
    }

    @Override
    public Optional<ConversationDTO> findById(String conversationId) {
        return Optional.ofNullable(baseMapper.selectById(conversationId)).map(this::toDto);
    }

    @Override
    public List<ConversationDTO> list(String agentKey, Long apiKeyId, boolean archived) {
        if (apiKeyId == null) {
            // 无主会话不构成「某个调用方的会话列表」——门户永远带 Key，这里只是防御
            return List.of();
        }
        var rows = baseMapper.selectList(
                QueryBuilder.lambda(RuntimeConversation.class)
                        .eq(RuntimeConversation::getAgentKey, agentKey)
                        .eq(RuntimeConversation::getApiKeyId, apiKeyId)
                        .isNull(!archived, RuntimeConversation::getArchivedAt)
                        .isNotNull(archived, RuntimeConversation::getArchivedAt)
                        .orderByDesc(RuntimeConversation::getUpdatedAt)
                        .build());
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public boolean rename(String conversationId, String name) {
        var entity = new RuntimeConversation();
        entity.setId(conversationId);
        entity.setName(name);
        // 用户改名即冻结：模型标题的异步回写只认 AUTO，不会盖掉用户输入
        entity.setNameSource(ConversationNaming.SOURCE_USER);
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    public boolean renameIfAutoNamed(String conversationId, String name) {
        var update = new LambdaUpdateWrapper<RuntimeConversation>()
                .eq(RuntimeConversation::getId, conversationId)
                .eq(RuntimeConversation::getNameSource, ConversationNaming.SOURCE_AUTO)
                .set(RuntimeConversation::getName, name);
        return baseMapper.update(null, update) > 0;
    }

    @Override
    public boolean setArchived(String conversationId, boolean archived) {
        var update = new LambdaUpdateWrapper<RuntimeConversation>()
                .eq(RuntimeConversation::getId, conversationId)
                .set(RuntimeConversation::getArchivedAt, archived ? OffsetDateTime.now() : null);
        return baseMapper.update(null, update) > 0;
    }

    /**
     * 归属不匹配与不存在返回完全相同的 404：不泄露「这个会话 ID 存在但不属于你」。
     */
    private V5aiException notFound(String conversationId) {
        log.warn("conversation {} is not accessible by the current API key", conversationId);
        return new V5aiException(ErrorCode.NOT_FOUND, "会话不存在");
    }

    private ConversationDTO toDto(RuntimeConversation entity) {
        return new ConversationDTO(entity.getId(), entity.getAgentKey(), entity.getApiKeyId(),
                entity.getUserId(), entity.getName(), entity.getArchivedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
