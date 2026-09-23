package xin.v5ai.nb.runtime.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.agentscope.core.domain.RunUsage;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.rag.CitationPayload;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
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

    /** 引用跟着回答一起落库（仅助手消息有值）；无引用时写 NULL，而不是空 JSON。 */
    @Test
    void citationsAreWrittenWithTheAnswerOnly() {
        when(baseMapper.insert(any(RuntimeMessage.class))).thenReturn(1);
        var hits = List.of(new RagHit(5L, 13L, "文档.pdf", 20, "切片内容", 0.21));

        service.save(new SessionMessage("c-1", "a1", MessageRole.ASSISTANT, "正文").withCitations(hits));
        service.save(new SessionMessage("c-1", "a1", MessageRole.USER, "提问"));

        var captor = ArgumentCaptor.forClass(RuntimeMessage.class);
        verify(baseMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        var metadata = captor.getAllValues().get(0).getMetadata();
        assertThat(metadata).startsWith("{\"citations\":[");
        assertThat(metadata).contains("\"knowledgeBaseId\":5").contains("\"chunkIndex\":20");
        // 没有引用的一轮不写这一列（NULL = 这一轮没有引用，见 ADR-0009）
        assertThat(captor.getAllValues().get(1).getMetadata()).isNull();
    }

    /** 历史接口要把引用读回来（门户靠它渲染「引用（N 条）」折叠块）。 */
    @Test
    void historyCarriesTheCitationsBack() {
        var row = message(1L, MessageRole.ASSISTANT, "正文", 1, 2, 3);
        row.setMetadata(CitationPayload.toMetadata(
                List.of(new RagHit(5L, 13L, "文档.pdf", 20, "切片内容", 0.21))));
        when(baseMapper.selectVoList(any())).thenReturn(List.of(row));
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        var messages = service.findByConversationId("c-1");

        assertThat(messages).singleElement().satisfies(message ->
                assertThat(message.citations()).singleElement().satisfies(hit -> {
                    assertThat(hit.documentId()).isEqualTo(13L);
                    assertThat(hit.chunkIndex()).isEqualTo(20);
                    assertThat(hit.content()).isEqualTo("切片内容");
                }));
    }

    /** 元数据列损坏（或以后被别的语义占用）时读历史不崩：退化成「没有引用」。 */
    @Test
    void brokenMetadataDegradesToNoCitations() {
        var row = message(1L, MessageRole.ASSISTANT, "正文", 1, 2, 3);
        row.setMetadata("{\"somethingElse\":1}");
        when(baseMapper.selectVoList(any())).thenReturn(List.of(row));
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        assertThat(service.findByConversationId("c-1")).singleElement()
                .satisfies(message -> assertThat(message.citations()).isEmpty());
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
     * 记忆窗口与摘要的取数**不投影思考与引用**：两者都可能很长（引用实测约 8.5KB/条消息），
     * 而这两条路径每轮都要跑，读了也只会被丢掉（都不回放给模型）。
     * 有人把投影改回全列时这条会红——少写一个列名不会报错，只会悄悄多读一屏大文本。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void hotPathQueriesExcludeTheBigReadOnlyColumns() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of());
        when(attachmentService.findByMessageIds(any())).thenReturn(Map.of());

        service.findRecentByConversationId("c-1", 20);
        service.findActiveBetween("c-1", null, 99L, 20);

        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(baseMapper, org.mockito.Mockito.times(2)).selectVoList(captor.capture());
        for (var wrapper : captor.getAllValues()) {
            assertThat(wrapper.getSqlSelect()).doesNotContain("reasoning").doesNotContain("metadata");
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
