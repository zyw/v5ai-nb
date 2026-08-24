//package xin.v5ai.nb.common.core.platform;
//
//import xin.v5ai.nb.common.core.page.PageQuery;
//import xin.v5ai.nb.common.core.page.PageResult;
//import xin.v5ai.nb.common.core.platform.entity.AuditLogDTO;
//import xin.v5ai.nb.common.core.platform.entity.bo.AuditLogListBo;
//
///**
// * 审计记录器：记录管理面操作。实现允许 no-op（如测试环境）。
// */
//public interface AuditLogRecorderService {
//
//    /**
//     * 记录一条审计。
//     *
//     * @param tenantId   租户（可为 null）
//     * @param actor      操作者（可为 null）
//     * @param action     动作
//     * @param targetType 目标类型（可为 null）
//     * @param targetId   目标 ID（可为 null）
//     * @param detail     详情（可为 null）
//     * @param ip         来源 IP（可为 null）
//     */
//    void record(Long tenantId, String actor, String action,
//                String targetType, String targetId, String detail, String ip);
//
////    List<AuditLogDTO> search(String actor, String action, int limit);
//
//    /**
//     * 分页查询审计日志列表。
//     *
//     * @param bo     筛选条件
//     * @param pageQuery 分页条件
//     * @return 审计日志列表
//     */
//    PageResult<AuditLogDTO> queryPageList(AuditLogListBo bo, PageQuery pageQuery);
//}
