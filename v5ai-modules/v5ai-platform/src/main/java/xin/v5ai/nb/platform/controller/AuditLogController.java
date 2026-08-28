//package xin.v5ai.nb.platform.controller;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import xin.v5ai.nb.common.core.domain.R;
//import xin.v5ai.nb.common.core.page.PageQuery;
//import xin.v5ai.nb.common.core.domain.PageResult;
//import xin.v5ai.nb.common.core.platform.AuditLogRecorderService;
//import xin.v5ai.nb.common.core.platform.entity.AuditLogDTO;
//import xin.v5ai.nb.common.core.platform.entity.bo.AuditLogListBo;
//import xin.v5ai.nb.common.web.core.BaseController;
//
///**
// * 审计日志查询 API。
// */
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/api/admin/audit-logs")
//public class AuditLogController extends BaseController {
//
//    private final AuditLogRecorderService service;
//
//    @GetMapping
//    public R<PageResult<AuditLogDTO>> pageList(AuditLogListBo bo, PageQuery pageQuery) {
//        return R.ok(service.queryPageList(bo, pageQuery));
//    }
//}
