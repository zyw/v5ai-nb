package xin.v5ai.nb.common.agentscope.core.history;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 历史窗口裁剪：最近优先、至少一条、保底条数、首条对齐到用户提问。
 */
class HistoryWindowTest {

    private static SessionMessage message(long id, MessageRole role, String content) {
        return new SessionMessage("c", "agent-x", role, content).withMessageId(id);
    }

    /** 交替问答序列：问1 答1 问2 答2 … */
    private static List<SessionMessage> turns(int count) {
        var history = new ArrayList<SessionMessage>();
        long id = 1;
        for (int i = 1; i <= count; i++) {
            history.add(message(id++, MessageRole.USER, "问" + i));
            history.add(message(id++, MessageRole.ASSISTANT, "答" + i));
        }
        return history;
    }

    @Test
    void emptyHistoryStaysEmpty() {
        var result = HistoryWindow.unlimited().select(List.of());
        assertThat(result.messages()).isEmpty();
        assertThat(result.trimmed()).isFalse();
        assertThat(result.keptChars()).isZero();
    }

    @Test
    void withinBudgetKeepsEverythingAndReportsNoTrim() {
        var history = turns(3);
        var result = new HistoryWindow(40, 8000, 2, true).select(history);
        assertThat(result.messages()).isEqualTo(history);
        assertThat(result.trimmed()).isFalse();
        assertThat(result.droppedMessages()).isZero();
        assertThat(result.droppedChars()).isZero();
    }

    @Test
    void messageBudgetKeepsTheNewestTail() {
        var result = new HistoryWindow(4, 10_000, 2, true).select(turns(10));

        assertThat(result.keptMessages()).isEqualTo(4);
        assertThat(result.droppedMessages()).isEqualTo(16);
        assertThat(result.trimmed()).isTrue();
        assertThat(result.messages()).extracting(SessionMessage::content)
                .containsExactly("问9", "答9", "问10", "答10");
    }

    @Test
    void charBudgetKeepsTheNewestTail() {
        var history = List.of(
                message(1, MessageRole.USER, "aaaaaaaaaa"),
                message(2, MessageRole.ASSISTANT, "bbbbbbbbbb"),
                message(3, MessageRole.USER, "cccccccccc"),
                message(4, MessageRole.ASSISTANT, "dddddddddd"));

        var result = new HistoryWindow(100, 25, 1, true).select(history);

        assertThat(result.keptMessages()).isEqualTo(2);
        assertThat(result.droppedChars()).isEqualTo(20);
        assertThat(result.keptChars()).isEqualTo(20);
        assertThat(result.messages()).extracting(SessionMessage::content)
                .containsExactly("cccccccccc", "dddddddddd");
    }

    /** 保底条数不受预算约束：字符预算再紧也要凑够 minMessages 条。 */
    @Test
    void minMessagesWinsOverTheBudget() {
        var history = List.of(
                message(1, MessageRole.USER, "aaaaaaaaaa"),
                message(2, MessageRole.ASSISTANT, "bbbbbbbbbb"),
                message(3, MessageRole.USER, "cccccccccc"),
                message(4, MessageRole.ASSISTANT, "dddddddddd"),
                message(5, MessageRole.USER, "eeeeeeeeee"));

        var result = new HistoryWindow(100, 5, 3, false).select(history);

        assertThat(result.keptMessages()).isEqualTo(3);
        assertThat(result.messages()).extracting(SessionMessage::content)
                .containsExactly("cccccccccc", "dddddddddd", "eeeeeeeeee");
    }

    /** 裁剪后首条不能是助手回答：没有对应提问的回答会让模型串味。 */
    @Test
    void firstKeptMessageIsAlignedToAUserTurn() {
        var aligned = new HistoryWindow(3, 10_000, 1, true).select(turns(5));
        assertThat(aligned.messages()).extracting(SessionMessage::content)
                .containsExactly("问5", "答5");
        assertThat(aligned.droppedMessages()).isEqualTo(8);

        // 关掉对齐时保留原样的三条
        var raw = new HistoryWindow(3, 10_000, 1, false).select(turns(5));
        assertThat(raw.messages()).extracting(SessionMessage::content)
                .containsExactly("答4", "问5", "答5");
    }

    @Test
    void unlimitedKeepsEverythingAndFetchesWithoutLimit() {
        var window = HistoryWindow.unlimited();
        assertThat(window.fetchLimit()).isEqualTo(-1);
        assertThat(window.select(turns(50)).keptMessages()).isEqualTo(100);
    }

    /** SQL 侧多取几倍：对齐还要再丢几条，取刚刚好会把窗口喂不满。 */
    @Test
    void fetchLimitLeavesRoomForAlignment() {
        assertThat(new HistoryWindow(40, 8000, 2, true).fetchLimit()).isEqualTo(80);
        assertThat(new HistoryWindow(2, 8000, 2, true).fetchLimit()).isEqualTo(20);
    }
}
