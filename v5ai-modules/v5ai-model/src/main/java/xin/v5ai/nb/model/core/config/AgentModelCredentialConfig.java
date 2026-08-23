//package xin.v5ai.nb.model.runtime.config;
//
//import tools.jackson.databind.ObjectMapper;
//
//public record AgentModelCredentialConfig(String apiKey, String baseUrl, Double temperature, Integer maxTokens) {
//    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
//
//    public static AgentModelCredentialConfig parse(String json) {
//        try {
//            var node = OBJECT_MAPPER.readTree(json);
//            return new AgentModelCredentialConfig(
//                    textOrNull(node, "apiKey"),
//                    textOrNull(node, "baseUrl"),
//                    node.hasNonNull("temperature") ? node.get("temperature").asDouble() : null,
//                    node.hasNonNull("maxTokens") ? node.get("maxTokens").asInt() : null
//            );
//        } catch (Exception exception) {
//            throw new IllegalArgumentException("invalid model credential json", exception);
//        }
//    }
//
//    private static String textOrNull(tools.jackson.databind.JsonNode node, String field) {
//        return node.hasNonNull(field) ? node.get(field).asText() : null;
//    }
//}
