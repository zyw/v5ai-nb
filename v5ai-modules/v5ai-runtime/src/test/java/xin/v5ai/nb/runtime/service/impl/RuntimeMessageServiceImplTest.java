package xin.v5ai.nb.runtime.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.agentscope.core.domain.RunUsage;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.runtime.core.service.MessageAttachmentService;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.domain.vo.RuntimeMessageVo;
import xin.v5ai.nb.runtime.mapper.RuntimeMessageMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用量随回答落库与读回：事件流里的「用量 / 用时」只活在那一次 SSE 里，
 * 刷新页面后要靠 v5ai_message 的三列重新给出，因此这两个方向都必须钉住。
 */
class RuntimeMessageServiceImplTest {

    private RuntimeMessageMapper baseMapper;
    private MessageAttachmentService attachmentService;
    private RuntimeMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境无 MyBatis 启动流程，手动初始化实体 TableInfo，使 LambdaQueryWrapper 能解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RuntimeMessage.class);

        baseMapper = mock(RuntimeMessageMapper.class);
        attachmentService = mock(MessageAttachmentService.class);
        service = new RuntimeMessageServiceImpl(baseMapper, attachmentService);
    }

    @Test
    void usageIsWrittenToTheMessageColumns() {
        when(baseMapper.insert(any(RuntimeMessage.class))).thenReturn(1);

        service.save(new SessionMessage("c-1", "a1", MessageRole.ASSISTANT, "正文")
                .withUsage(RunUsage.of(120, 480, 3210)));

        var captor = ArgumentCaptor.forClass(RuntimeMessage.class);
        verify(baseMapper).insert(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getPromptTokens()).isEqualTo(120);
        assertThat(entity.getCompletionTokens()).isEqualTo(480);
        assertThat(entity.getDurationMs()).isEqualTo(3210);
    }

    /** 用户消息本来就没有用量：三列留空，不要写成 0（0 会被历史侧当成"有用量但为 0"）。 */
    @Test
    void messageWithoutUsageLeavesTheColumnsNull() {
        when(baseMapper.insert(any(RuntimeMessage.class))).thenReturn(1);

        service.save(new SessionMessage("c-1", "a1", MessageRole.USER, "提问"));

        var captor = ArgumentCaptor.forClass(RuntimeMessage.class);
        verify(baseMapper).insert(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getPromptTokens()).isNull();
        assertThat(entity.getCompletionTokens()).isNull();
        assertThat(entity.getDurationMs()).isNull();
    }

    /** 读回：齐备的行还原用量，V39 之前的历史行（三列为 NULL）保持没有用量。 */
    @Test
    void historyRebuildsUsageAndLeavesLegacyRowsWithoutIt() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
                message(1L, MessageRole.ASSISTANT, "新回答", 120, 480, 3210),
                message(2L, MessageRole.ASSISTANT, "旧回答", null, null, null)));
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        var messages = service.findByConversationId("c-1");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).usage()).isNotNull();
        assertThat(messages.get(0).usage().totalTokens()).isEqualTo(600);
        assertThat(messages.get(0).usage().durationMs()).isEqualTo(3210);
        assertThat(messages.get(1).usage()).isNull();
    }

    /** 思考跟着回答一起落库（仅助手消息有值；用户消息留空）。 */
    @Test
    void reasoningIsWrittenWithTheAnswerOnly() {
        when(baseMapper.insert(any(RuntimeMessage.class))).thenReturn(1);

        service.save(new SessionMessage("c-1", "a1", MessageRole.ASSISTANT, "正文")
                .withReasoning("先看 A，再看 B"));
        service.save(new SessionMessage("c-1", "a1", MessageRole.USER, "提问"));

        var captor = ArgumentCaptor.forClass(RuntimeMessage.class);
        verify(baseMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getReasoning()).isEqualTo("先看 A，再看 B");
        assertThat(captor.getAllValues().get(1).getReasoning()).isNull();
    }

    /** 历史接口要把思考读回来（门户靠它渲染折叠的「思考过程」）。 */
    @Test
    void historyCarriesTheReasoningBack() {
        var row = message(1L, MessageRole.ASSISTANT, "正文", 1, 2, 3);
        row.setReasoning("思考正文");
        when(baseMapper.selectVoList(any())).thenReturn(List.of(row));
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        var messages = service.findByConversationId("c-1");

        assertThat(messages).singleElement()
                .satisfies(message -> assertThat(message.reasoning()).isEqualTo("思考正文"));
    }

    /**
     * 记忆窗口与摘要的取数**不投影思考**：它可能很长，而这两条路径每轮都要跑，
     * 读了也只会被丢掉（思考不回放给模型）。有人把投影改回全列时这条会红。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void hotPathQueriesExcludeTheReasoningColumn() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of());
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        service.findRecentByConversationId("c-1", 20);
        service.findActiveBetween("c-1", null, 99L, 20);

        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(baseMapper, org.mockito.Mockito.times(2)).selectVoList(captor.capture());
        for (var wrapper : captor.getAllValues()) {
            assertThat(wrapper.getSqlSelect()).doesNotContain("reasoning");
            // 该有的列一个都不能少：剔除只针对大列，不是把投影写空
            assertThat(wrapper.getSqlSelect()).contains("content").contains("prompt_tokens");
        }
    }

    /** 读取侧恒排除作废消息（历史接口 / resume / 记忆回放共用这条查询）。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void historyQueryExcludesSupersededMessages() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of());

        service.findByConversationId("c-1");

        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(baseMapper).selectVoList(captor.capture());
        var segment = captor.getValue().getSqlSegment();
        assertThat(segment).contains("conversation_id").contains("superseded_at IS NULL");
    }

    /**
     * 作废范围是「锚点之后」：锚点提问本身保持有效——重新生成复用它、不新插提问行，
     * 所以历史里不会出现两条相同的提问。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void supersedeAfterMarksOnlyTheMessagesAfterTheAnchor() {
        when(baseMapper.update(any(), any())).thenReturn(2);

        var marked = service.supersedeAfter("c-1", 55L);

        assertThat(marked).isEqualTo(2);
        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        var wrapper = captor.getValue();
        assertThat(wrapper.getSqlSet()).contains("superseded_at");
        assertThat(wrapper.getSqlSegment()).contains("conversation_id").contains("id >").contains("superseded_at IS NULL");
    }

    /** 锚点取值：按主键取未作废消息，并把主键带回领域对象（前端要拿它当锚点）。 */
    @Test
    void findActiveMessageCarriesThePrimaryKey() {
        var entity = new RuntimeMessage();
        entity.setId(55L);
        entity.setConversationId("c-1");
        entity.setAgentKey("a1");
        entity.setRole(MessageRole.USER.name());
        entity.setContent("提问");
        when(baseMapper.selectOne(any())).thenReturn(entity);
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        var found = service.findActiveMessage(55L);

        assertThat(found).isPresent();
        assertThat(found.get().messageId()).isEqualTo(55L);
        assertThat(found.get().content()).isEqualTo("提问");
    }

    @Test
    void findActiveMessageIsEmptyForMissingOrNullId() {
        when(baseMapper.selectOne(any())).thenReturn(null);

        assertThat(service.findActiveMessage(55L)).isEmpty();
        assertThat(service.findActiveMessage(null)).isEmpty();
    }

    private RuntimeMessageVo message(Long id, MessageRole role, String content,
                                     Integer promptTokens, Integer completionTokens, Integer durationMs) {
        var vo = new RuntimeMessageVo();
        vo.setId(id);
        vo.setConversationId("c-1");
        vo.setAgentKey("a1");
        vo.setRole(role.name());
        vo.setContent(content);
        vo.setPromptTokens(promptTokens);
        vo.setCompletionTokens(completionTokens);
        vo.setDurationMs(durationMs);
        return vo;
    }
}
