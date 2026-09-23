package xin.v5ai.nb.workflow.core;

import java.util.Objects;

/**
 * 条件求值：白名单运算符，避免任意代码执行。
 */
public final class ConditionEvaluator {
    private ConditionEvaluator() {
    }

    public static boolean evaluate(Object left, String operator, Object right) {
        if (operator == null) {
            throw new IllegalArgumentException("condition operator is required");
        }
        return switch (operator) {
            case "==" -> Objects.equals(stringify(left), stringify(right));
            case "!=" -> !Objects.equals(stringify(left), stringify(right));
            case "contains" -> stringify(left).contains(stringify(right));
            case "not_contains" -> !stringify(left).contains(stringify(right));
            case "is_empty" -> isEmpty(left);
            case "is_not_empty" -> !isEmpty(left);
            case ">" -> compare(left, right) > 0;
            case ">=" -> compare(left, right) >= 0;
            case "<" -> compare(left, right) < 0;
            case "<=" -> compare(left, right) <= 0;
            default -> throw new IllegalArgumentException("unsupported condition operator: " + operator);
        };
    }

    private static boolean isEmpty(Object value) {
        return value == null || stringify(value).isBlank();
    }

    private static double compare(Object left, Object right) {
        try {
            return Double.parseDouble(stringify(left)) - Double.parseDouble(stringify(right));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("operator requires numeric operands", exception);
        }
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
