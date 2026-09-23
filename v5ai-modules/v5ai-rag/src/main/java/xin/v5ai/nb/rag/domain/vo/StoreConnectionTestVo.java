package xin.v5ai.nb.rag.domain.vo;

/**
 * 存储实例连接测试结果。
 *
 * @param ok      是否连通
 * @param message 成功 / 失败原因
 */
public record StoreConnectionTestVo(boolean ok, String message) {

    public static StoreConnectionTestVo success(String message) {
        return new StoreConnectionTestVo(true, message);
    }

    public static StoreConnectionTestVo failure(String message) {
        return new StoreConnectionTestVo(false, message);
    }
}
