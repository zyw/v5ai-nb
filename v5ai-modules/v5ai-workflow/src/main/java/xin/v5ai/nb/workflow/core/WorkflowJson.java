package xin.v5ai.nb.workflow.core;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow 领域 JSON 编解码：定义（nodes/edges）与运行输入/输出（Map）统一在此序列化。
 * 存储层（v5ai-infrastructure）将 JSON 作为 TEXT 落库，读取时经此还原为强类型。
 */
public final class WorkflowJson {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowJson() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("failed to serialize workflow json", exception);
        }
    }

    public static WorkflowDefinition parseDefinition(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, WorkflowDefinition.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("failed to parse workflow definition", exception);
        }
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("failed to parse workflow json map", exception);
        }
    }
}
