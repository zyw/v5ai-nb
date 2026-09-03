package xin.v5ai.nb.common.milvus.domain;

/**
 * Milvus 连接测试结果。
 *
 * @param ok      是否连通
 * @param message 成功 / 失败原因
 */
public record ConnectionTestResult(boolean ok, String message) {

    public static ConnectionTestResult success(String message) {
        return new ConnectionTestResult(true, message);
    }

    public static ConnectionTestResult failure(String message) {
        return new ConnectionTestResult(false, message);
    }
}
