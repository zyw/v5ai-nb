package xin.v5ai.nb.workflow;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.workflow.core.ConditionEvaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionEvaluatorTest {

    @Test
    void equalityAndInequality() {
        assertThat(ConditionEvaluator.evaluate("a", "==", "a")).isTrue();
        assertThat(ConditionEvaluator.evaluate("a", "==", "b")).isFalse();
        assertThat(ConditionEvaluator.evaluate("a", "!=", "b")).isTrue();
        assertThat(ConditionEvaluator.evaluate(1, "==", 1)).isTrue();
        // 宽松类型：数字与字符串比较时统一转字符串
        assertThat(ConditionEvaluator.evaluate(1, "==", "1")).isTrue();
    }

    @Test
    void containsOperators() {
        assertThat(ConditionEvaluator.evaluate("hello world", "contains", "world")).isTrue();
        assertThat(ConditionEvaluator.evaluate("hello", "contains", "world")).isFalse();
        assertThat(ConditionEvaluator.evaluate("hello", "not_contains", "world")).isTrue();
        assertThat(ConditionEvaluator.evaluate("hello world", "not_contains", "world")).isFalse();
    }

    @Test
    void emptinessOperators() {
        assertThat(ConditionEvaluator.evaluate(null, "is_empty", null)).isTrue();
        assertThat(ConditionEvaluator.evaluate("", "is_empty", null)).isTrue();
        assertThat(ConditionEvaluator.evaluate("  ", "is_empty", null)).isTrue();
        assertThat(ConditionEvaluator.evaluate("x", "is_empty", null)).isFalse();
        assertThat(ConditionEvaluator.evaluate("x", "is_not_empty", null)).isTrue();
        assertThat(ConditionEvaluator.evaluate("", "is_not_empty", null)).isFalse();
    }

    @Test
    void numericComparison() {
        assertThat(ConditionEvaluator.evaluate(20, ">", "10")).isTrue();
        assertThat(ConditionEvaluator.evaluate(5, ">", "10")).isFalse();
        assertThat(ConditionEvaluator.evaluate(10, ">=", "10")).isTrue();
        assertThat(ConditionEvaluator.evaluate(5, "<", "10")).isTrue();
        assertThat(ConditionEvaluator.evaluate(10, "<=", "10")).isTrue();
    }

    @Test
    void numericComparisonRejectsNonNumeric() {
        assertThatThrownBy(() -> ConditionEvaluator.evaluate("abc", ">", "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void rejectsUnsupportedOperator() {
        assertThatThrownBy(() -> ConditionEvaluator.evaluate("a", "eval", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void rejectsNullOperator() {
        assertThatThrownBy(() -> ConditionEvaluator.evaluate("a", null, "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
    }
}
