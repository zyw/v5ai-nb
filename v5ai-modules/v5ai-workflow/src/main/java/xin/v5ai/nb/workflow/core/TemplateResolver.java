package xin.v5ai.nb.workflow.core;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量模板解析：支持 {@code {{varName}}} 与点号路径 {@code {{a.b.c}}}。
 */
public final class TemplateResolver {
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    private TemplateResolver() {
    }

    /** 把模板串里的 {{var}} 全部替换为变量值字符串。 */
    public static String resolve(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = TEMPLATE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = lookup(matcher.group(1), variables);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 若整个表达式是单个 {{var}}，返回其原始值（保留类型）；否则按模板解析为字符串。 */
    public static Object resolveRefOrLiteral(String expression, Map<String, Object> variables) {
        if (expression == null) {
            return null;
        }
        Matcher matcher = TEMPLATE.matcher(expression.trim());
        if (matcher.matches()) {
            return lookup(matcher.group(1), variables);
        }
        return resolve(expression, variables);
    }

    private static Object lookup(String dottedPath, Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }
        JsonNode node = WorkflowJson.MAPPER.valueToTree(variables);
        for (String part : dottedPath.split("\\.")) {
            if (node == null) {
                return null;
            }
            node = node.get(part);
        }
        return toObject(node);
    }

    private static Object toObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node;
    }
}
