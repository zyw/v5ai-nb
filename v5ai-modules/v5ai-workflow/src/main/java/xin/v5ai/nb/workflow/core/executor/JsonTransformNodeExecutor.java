package xin.v5ai.nb.workflow.core.executor;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.TemplateResolver;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds JSON values through explicit path selection, constants, and array projection. */
@Component
public class JsonTransformNodeExecutor implements WorkflowNodeExecutor {
    private static final Pattern TARGET = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern PATH_TOKEN = Pattern.compile("(?:^|\\.)([A-Za-z_][A-Za-z0-9_]*)|\\[(\\d+)]");

    @Override public WorkflowNodeType type() { return WorkflowNodeType.JSON_TRANSFORM; }

    public static void validateConfig(Map<String, Object> config) {
        Object source = config.get("source");
        if (source != null && String.valueOf(source).isBlank()) {
            throw new IllegalArgumentException("JSON transform source is required");
        }
        Object outputVar = config.get("outputVar");
        if (outputVar != null && !String.valueOf(outputVar).isBlank()
                && !TARGET.matcher(String.valueOf(outputVar)).matches()) {
            throw new IllegalArgumentException("JSON transform outputVar must be a simple identifier");
        }
        Object rawMappings = config.get("mappings");
        if (!(rawMappings instanceof List<?> mappings) || mappings.isEmpty()) {
            throw new IllegalArgumentException("JSON transform requires at least one mapping");
        }
        validateMappings(mappings, false);
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        Object sourceConfig = node.config().get("source");
        Object rawSource = TemplateResolver.resolveRefOrLiteral(
                sourceConfig == null ? "{{inputs}}" : String.valueOf(sourceConfig), context);
        JsonNode source = WorkflowJson.MAPPER.valueToTree(rawSource);
        Object rawMappings = node.config().get("mappings");
        if (!(rawMappings instanceof List<?> mappings) || mappings.isEmpty()) {
            throw new IllegalArgumentException("JSON transform requires at least one mapping");
        }

        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Object item : mappings) {
            if (!(item instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException("JSON transform mapping must be an object");
            }
            String target = text(mapping.get("target"));
            validateTarget(target);
            if (outputs.containsKey(target)) {
                throw new IllegalArgumentException("duplicate JSON transform target: " + target);
            }
            String mode = text(mapping.get("mode"));
            Object value = switch (mode == null ? "PATH" : mode) {
                case "PATH" -> toValue(readPath(source, requiredPath(mapping.get("path"))));
                case "CONSTANT" -> parseConstant(mapping.get("valueJson"));
                case "ARRAY_MAP" -> mapArray(source, mapping);
                default -> throw new IllegalArgumentException("unsupported JSON transform mode: " + mode);
            };
            outputs.put(target, value);
        }

        String outputVar = AgentNodeExecutor.text(node.config().get("outputVar"));
        if (outputVar != null && !outputVar.isBlank()) context.setVariable(outputVar, outputs);
        return NodeExecutionResult.of(outputs);
    }

    private List<Object> mapArray(JsonNode source, Map<?, ?> mapping) {
        JsonNode array = readPath(source, requiredPath(mapping.get("path")));
        if (!array.isArray()) throw new IllegalArgumentException("JSON transform array mapping path must point to an array");
        Object rawFields = mapping.get("fields");
        if (!(rawFields instanceof List<?> fields) || fields.isEmpty()) {
            throw new IllegalArgumentException("JSON transform array mapping requires at least one field");
        }
        List<Object> result = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (!element.isObject() && !element.isArray()) {
                throw new IllegalArgumentException("JSON transform array items must be objects or arrays");
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            for (Object rawField : fields) {
                if (!(rawField instanceof Map<?, ?> field)) {
                    throw new IllegalArgumentException("JSON transform array field must be an object");
                }
                String target = text(field.get("target"));
                validateTarget(target);
                if (projected.containsKey(target)) {
                    throw new IllegalArgumentException("duplicate JSON transform array target: " + target);
                }
                projected.put(target, toValue(readPath(element, requiredPath(field.get("path")))));
            }
            result.add(projected);
        }
        return result;
    }

    private JsonNode readPath(JsonNode root, String path) {
        if (path.equals("$") || path.isEmpty()) return root;
        String normalized = normalizePath(path);
        Matcher matcher = PATH_TOKEN.matcher(normalized);
        JsonNode current = root;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw invalidPath(path);
            String key = matcher.group(1);
            if (key != null) {
                current = current == null ? null : current.get(key);
            } else {
                int index;
                try { index = Integer.parseInt(matcher.group(2)); }
                catch (NumberFormatException e) { throw invalidPath(path); }
                current = current == null || !current.isArray() ? null : current.get(index);
            }
            end = matcher.end();
        }
        if (end != normalized.length() || current == null) {
            throw new IllegalArgumentException("JSON transform path does not exist: " + path);
        }
        return current;
    }

    private static void validateMappings(List<?> mappings, boolean arrayField) {
        if (mappings.isEmpty()) throw new IllegalArgumentException("JSON transform mapping list must not be empty");
        java.util.Set<String> targets = new java.util.HashSet<>();
        for (Object raw : mappings) {
            if (!(raw instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException("JSON transform mapping must be an object");
            }
            String target = mapping.get("target") == null ? null : String.valueOf(mapping.get("target"));
            if (target == null || !TARGET.matcher(target).matches()) {
                throw new IllegalArgumentException("JSON transform target must be a simple identifier");
            }
            if (!targets.add(target)) throw new IllegalArgumentException("duplicate JSON transform target: " + target);

            String mode = mapping.get("mode") == null ? "PATH" : String.valueOf(mapping.get("mode"));
            switch (mode) {
                case "PATH" -> validatePathSyntax(requiredPath(mapping.get("path")));
                case "CONSTANT" -> parseConstant(mapping.get("valueJson"));
                case "ARRAY_MAP" -> {
                    if (arrayField) throw new IllegalArgumentException("nested JSON transform array mapping is not supported");
                    validatePathSyntax(requiredPath(mapping.get("path")));
                    Object rawFields = mapping.get("fields");
                    if (!(rawFields instanceof List<?> fields) || fields.isEmpty()) {
                        throw new IllegalArgumentException("JSON transform array mapping requires at least one field");
                    }
                    validateMappings(fields, true);
                }
                default -> throw new IllegalArgumentException("unsupported JSON transform mode: " + mode);
            }
        }
    }

    private static void validatePathSyntax(String path) {
        if (path.equals("$") || path.isEmpty()) return;
        String normalized = normalizePath(path);
        Matcher matcher = PATH_TOKEN.matcher(normalized);
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("JSON transform path is invalid: " + path);
            end = matcher.end();
        }
        if (end != normalized.length()) throw new IllegalArgumentException("JSON transform path is invalid: " + path);
    }

    private static String normalizePath(String path) {
        if (path.startsWith("$.")) return path.substring(2);
        if (path.startsWith("$[")) return path.substring(1);
        if (path.startsWith("$")) throw new IllegalArgumentException("JSON transform path is invalid: " + path);
        return path;
    }

    private static String requiredPath(Object raw) {
        String path = raw == null ? null : String.valueOf(raw);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("JSON transform path is required");
        return path.trim();
    }

    private static Object parseConstant(Object raw) {
        String json = raw == null ? null : String.valueOf(raw);
        if (json == null || json.isBlank()) throw new IllegalArgumentException("JSON transform constant must be valid JSON");
        try {
            return toValue(WorkflowJson.MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON transform constant must be valid JSON", e);
        }
    }

    private static Object toValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.properties().forEach(entry -> map.put(entry.getKey(), toValue(entry.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>(node.size());
            node.forEach(value -> values.add(toValue(value)));
            return values;
        }
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isIntegralNumber()) return node.numberValue();
        if (node.isNumber()) return node.asDouble();
        return node.asText();
    }

    private void validateTarget(String target) {
        if (target == null || !TARGET.matcher(target).matches()) {
            throw new IllegalArgumentException("JSON transform target must be a simple identifier");
        }
    }

    private IllegalArgumentException invalidPath(String path) {
        return new IllegalArgumentException("JSON transform path is invalid: " + path);
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
}
