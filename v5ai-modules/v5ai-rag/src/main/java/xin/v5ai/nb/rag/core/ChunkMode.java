package xin.v5ai.nb.rag.core;

/**
 * 切片策略取值（与知识库 config.chunkParams.sliceStrategy 持久化字符串一致）。
 *
 * @author ZYW
 * @since 2026-09-08
 */
public enum ChunkMode {
    LENGTH("length"),
    DELIMITER("delimiter"),
    REGEX("regex"),
    SMART("smart");

    private final String value;

    ChunkMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 大小写不敏感解析；空或未知值返回 {@code null}。
     */
    public static ChunkMode from(String value) {
        if (value == null) {
            return null;
        }
        for (ChunkMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
