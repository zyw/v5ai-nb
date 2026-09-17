//package xin.v5ai.nb.runtime.core.gateway;
//
//import xin.v5ai.nb.runtime.core.domain.RuntimeRunEventEntity;
//
///**
// * 运行事件持久化网关：写入一次运行的每一条事件记录。
// * 由基础设施层实现（MyBatis），对接 {@code v5ai_run_event} 表。
// */
//public interface RuntimeRunEventGateway {
//    /**
//     * 插入一条运行事件（如 RUN_STARTED / TEXT_DELTA / RUN_FAILED 等）。
//     *
//     * @param entity 运行事件实体（runId/eventType/payload/createdAt）
//     */
//    void insertRunEvent(RuntimeRunEventEntity entity);
//}
