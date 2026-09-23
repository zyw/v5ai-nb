package xin.v5ai.nb.runtime.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.runtime.core.utils.ConversationNaming;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.domain.RuntimeConversation;
import xin.v5ai.nb.runtime.mapper.RuntimeConversationMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

    /**
     * 让 LambdaUpdateWrapper 能解析出列名（{@code name_source}）：单元测试里没有 MyBatis 启动过程，
     * 不注册表信息的话 {@code getSqlSegment()} 取不到列名——而「条件写在 WHERE 里」正是本题要断言的东西。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RuntimeConversation.class);
    }

    @BeforeEach
    void setUp() {
        baseMapper = mock(RuntimeConversationMapper.class);
        service = new RuntimeConversationServiceImpl(baseMapper);
    }

    @Test
    void aNewConversationIsCreatedWithItsOwningKeyAndTheFallbackName() {
        when(baseMapper.selectById("c-1")).thenReturn(null);

        // 返回 true = 本次真的新建了会话，调用方据此决定要不要在首轮结束后改写标题
        var created = service.ensureConversation(new ConversationDTO(
                "c-1", "a1", API_KEY_ID, 7L, "帮我看看这张图", null, null, null));

        assertThat(created).isTrue();
        var captor = ArgumentCaptor.forClass(RuntimeConversation.class);
        verify(baseMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("帮我看看这张图");
        // 系统生成的名字才允许被模型标题改写
        assertThat(captor.getValue().getNameSource()).isEqualTo(ConversationNaming.SOURCE_AUTO);
    }

    /** 提问为空（纯图片提问）时不命名：列保持 null，前端显示「新会话」。 */
    @Test
    void aConversationWithoutAQuestionStaysUnnamed() {
        when(baseMapper.selectById("c-1")).thenReturn(null);

        service.ensureConversation(new ConversationDTO("c-1", "a1", API_KEY_ID, 7L, null, null, null, null));

        var captor = ArgumentCaptor.forClass(RuntimeConversation.class);
        verify(baseMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isNull();
        assertThat(captor.getValue().getNameSource()).isNull();
    }

    /** 已存在的会话即使带着名字进来也不会被改写：用户改过的名字必须留住。 */
    @Test
    void anExistingConversationKeepsItsName() {
        var existing = conversation(API_KEY_ID, null);
        existing.setName("用户自己起的名字");
        existing.setNameSource(ConversationNaming.SOURCE_USER);
        when(baseMapper.selectById("c-1")).thenReturn(existing);

        var created = service.ensureConversation(new ConversationDTO(
                "c-1", "a1", API_KEY_ID, 7L, "自动生成的名字", null, null, null));

        assertThat(created).isFalse();
        verify(baseMapper, never()).insert(any(RuntimeConversation.class));
        verify(baseMapper, never()).update(isNull(), any());
    }

    /** 用户改名即冻结：来源切成 USER，模型标题的异步回写（只认 AUTO）再也改不动它。 */
    @Test
    void renamingByTheUserFreezesTheNameAgainstAutomaticTitles() {
        when(baseMapper.updateById(any(RuntimeConversation.class))).thenReturn(1);

        assertThat(service.rename("c-1", "季度复盘")).isTrue();

        var captor = ArgumentCaptor.forClass(RuntimeConversation.class);
        verify(baseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("季度复盘");
        assertThat(captor.getValue().getNameSource()).isEqualTo(ConversationNaming.SOURCE_USER);
    }

    /**
     * 模型标题回写把「只能改系统生成的名字」写在 UPDATE 的 WHERE 里，
     * 而不是先查后写：用户改名与异步标题生成并发时由数据库裁决，不存在竞态窗口。
     */
    @Test
    void automaticTitlesOnlyRewriteNamesThatAreStillSystemGenerated() {
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.renameIfAutoNamed("c-1", "季度复盘")).isTrue();

        var captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("name_source")
                .contains("name");
        verify(baseMapper, never()).updateById(any(RuntimeConversation.class));
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
