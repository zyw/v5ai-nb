package xin.v5ai.nb.runtime.core.enums;

/**
 * 一次 Agent 运行（{@code v5ai_run.status}）的生命周期状态。
 *
 * <p>取代此前散落的裸字符串字面量。收尾态有三种，语义互不相同：
 * <ul>
 *   <li>{@link #COMPLETED}——模型正常答完；</li>
 *   <li>{@link #FAILED}——执行中抛错；</li>
 *   <li>{@link #CANCELED}——调用方主动停止（见 CONTEXT.md「停止」），是正常收尾而非异常。</li>
 * </ul>
 *
 * <p>状态迁移只允许从 {@link #RUNNING} 走向某个收尾态，且是单向的：
 * 完成/失败/取消都带 {@code WHERE status = 'RUNNING'} 条件，防止晚到的收尾动作
 * 覆盖已经落定的状态（例如「先点停止、随后模型又答完」）。
 */
public enum RunStatus {
    /** 进行中：已落库、尚未收尾 */
    RUNNING,
    /** 已完成：模型给出了完整回答 */
    COMPLETED,
    /** 已失败：执行过程抛错，错误信息写在 error_message */
    FAILED,
    /** 已取消：调用方主动停止，已生成的部分回答仍会落库 */
    CANCELED
}
