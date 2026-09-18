package xin.v5ai.nb.runtime.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.domain.RuntimeConversation;
import xin.v5ai.nb.runtime.mapper.RuntimeConversationMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话归属断言与归档拦截（ADR 0006 / CONTEXT.md「归档」）：
 * 门户侧会话只属于创建它的那把 Key，且归档后不得继续对话。
 */
class RuntimeConversationServiceImplTest {

    private static final long API_KEY_ID = 1L;

    private RuntimeConversationMapper baseMapper;
    private RuntimeConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        baseMapper = mock(RuntimeConversationMapper.class);
        service = new RuntimeConversationServiceImpl(baseMapper);
    }

    @Test
    void aNewConversationIsCreatedWithItsOwningKey() {
        when(baseMapper.selectById("c-1")).thenReturn(null);

        service.ensureConversation(new ConversationDTO("c-1", "a1", API_KEY_ID, 7L, null, null, null, null));

        verify(baseMapper).insert(any(RuntimeConversation.class));
    }

    @Test
    void aConversationOfAnotherKeyIsReportedAsMissing() {
        when(baseMapper.selectById("c-1")).thenReturn(conversation(99L, null));

        var failure = catchThrowableOfType(
                () -> service.ensureConversation(new ConversationDTO("c-1", "a1", API_KEY_ID, 7L,
                        null, null, null, null)),
                V5aiException.class);

        assertThat(failure.code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 调试入口产生的无主会话不能被 Key 认领：否则猜到 ID 就等于拿到它的全部历史。 */
    @Test
    void anOwnerlessDebugConversationCannotBeClaimedByAKey() {
        when(baseMapper.selectById("c-1")).thenReturn(conversation(null, null));

        assertThat(catchThrowableOfType(() -> service.ensureConversation(new ConversationDTO(
                "c-1", "a1", API_KEY_ID, 7L, null, null, null, null)), V5aiException.class).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void continuingAnArchivedConversationIsAConflict() {
        when(baseMapper.selectById("c-1")).thenReturn(conversation(API_KEY_ID, OffsetDateTime.now()));

        assertThat(catchThrowableOfType(() -> service.ensureConversation(new ConversationDTO(
                "c-1", "a1", API_KEY_ID, 7L, null, null, null, null)), V5aiException.class).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 管理端调试入口不带 Key：不做归属断言，但归档仍然拦得住。 */
    @Test
    void debugRunsSkipTheOwnershipAssertionButStillRespectArchiving() {
        when(baseMapper.selectById("c-1")).thenReturn(conversation(API_KEY_ID, null));

        service.ensureConversation(new ConversationDTO("c-1", "a1", null, 7L, null, null, null, null));

        verify(baseMapper, never()).insert(any(RuntimeConversation.class));

        when(baseMapper.selectById("c-1")).thenReturn(conversation(API_KEY_ID, OffsetDateTime.now()));
        assertThat(catchThrowableOfType(() -> service.ensureConversation(new ConversationDTO(
                "c-1", "a1", null, 7L, null, null, null, null)), V5aiException.class).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /**
     * 继续对话要把会话标记为「刚刚活跃」：列表按 updated_at 倒序，
     * 不刷新的话新建的会话永远排最前、最近聊过的沉底。
     */
    @Test
    void continuingAConversationBumpsItsActivityTimestamp() {
        when(baseMapper.selectById("c-1")).thenReturn(conversation(API_KEY_ID, null));

        service.ensureConversation(new ConversationDTO("c-1", "a1", API_KEY_ID, 7L, null, null, null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(RuntimeConversation.class);
        verify(baseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("c-1");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    private RuntimeConversation conversation(Long apiKeyId, OffsetDateTime archivedAt) {
        var entity = new RuntimeConversation();
        entity.setId("c-1");
        entity.setAgentKey("a1");
        entity.setApiKeyId(apiKeyId);
        entity.setArchivedAt(archivedAt);
        return entity;
    }
}
