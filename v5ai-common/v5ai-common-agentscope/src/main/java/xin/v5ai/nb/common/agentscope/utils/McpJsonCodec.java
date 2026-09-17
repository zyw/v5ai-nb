package xin.v5ai.nb.common.agentscope.utils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * MCP 领域 JSON 编解码小工具：args/headers/env/inputSchema 以 JSON 文本存储。
 * 运行时（连接工厂、Tool 解析器）与持久化层共用。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public final class McpJsonCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private McpJsonCodec() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize mcp json value", e);
        }
    }

    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Map<String, String> toStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, STRING_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static Map<String, Object> toObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, OBJECT_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
