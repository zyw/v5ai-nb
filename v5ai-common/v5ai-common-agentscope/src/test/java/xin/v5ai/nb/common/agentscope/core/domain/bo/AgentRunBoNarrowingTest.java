package xin.v5ai.nb.common.agentscope.core.domain.bo;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentRunBo} 的收窄项与附件透传：{@code with*} 链式方法在多次追加后不得丢掉任何一项
 * （record 是不可变的，任何一个 with 方法漏带字段都会静默丢配置）。
 */
class AgentRunBoNarrowingTest {

    @Test
    void nullListsAreNormalizedToEmptySoConsumersNeedNoNullChecks() {
        var bo = new AgentRunBo("a", "c", "q", null, null, null, null, null, null, null, null, null, false, null);

        assertThat(bo.history()).isEmpty();
        assertThat(bo.attachments()).isEmpty();
        assertThat(bo.disabledMcpServerIds()).isEmpty();
        assertThat(bo.disabledSkillIds()).isEmpty();
        assertThat(bo.hasAttachments()).isFalse();
        // 默认是「新的一轮」：不复用提问（只有「重新生成」才把它置为 true）
        assertThat(bo.reuseUserMessage()).isFalse();
    }

    @Test
    void everyNarrowingFieldSurvivesTheWholeWithChain() {
        var history = List.of(new SessionMessage("c", "a", MessageRole.USER, "上一轮"));

        var bo = new AgentRunBo("a", "c", "q")
                .withWebSearch(false)
                .withAttachments(List.of(AttachmentRef.image(5L)))
                .withDisabledMcpServerIds(List.of(7L, 8L))
                .withDisabledSkillIds(List.of(9L))
                .withHistory(history)
                .withRagContext("ctx")
                .withRunId("r1");

        assertThat(bo.agentKey()).isEqualTo("a");
        assertThat(bo.query()).isEqualTo("q");
        assertThat(bo.ragContext()).isEqualTo("ctx");
        assertThat(bo.runId()).isEqualTo("r1");
        assertThat(bo.history()).isEqualTo(history);
        assertThat(bo.attachments()).containsExactly(AttachmentRef.image(5L));
        assertThat(bo.disabledMcpServerIds()).containsExactly(7L, 8L);
        assertThat(bo.disabledSkillIds()).containsExactly(9L);
        assertThat(bo.resolveWebSearch(true)).isFalse();
        assertThat(bo.hasAttachments()).isTrue();
    }

    @Test
    void attachmentsUseTheImageTypeConstant() {
        assertThat(AttachmentRef.image(5L).isImage()).isTrue();
        assertThat(new AttachmentRef("image", 5L).isImage()).isTrue();
        assertThat(new AttachmentRef("DOCUMENT", 5L).isImage()).isFalse();
        assertThat(new AttachmentRef(null, 5L).isImage()).isFalse();
    }

    @Test
    void sessionMessageNormalizesAttachmentsAndKeepsLegacyConstructor() {
        var legacy = new SessionMessage("c", "a", MessageRole.USER, "q");
        assertThat(legacy.attachments()).isEmpty();

        var withAttachments = legacy.withAttachments(List.of(AttachmentRef.image(5L)));
        assertThat(withAttachments.attachments()).containsExactly(AttachmentRef.image(5L));
        assertThat(legacy.attachments()).isEmpty();
    }
}
