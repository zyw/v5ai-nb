//package xin.v5ai.nb.platform.service.impl;
//
//import cn.hutool.core.util.ObjectUtil;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import xin.v5ai.nb.common.core.page.PageQuery;
//import xin.v5ai.nb.common.core.page.PageResult;
//import xin.v5ai.nb.common.core.platform.AuditLogRecorderService;
//import xin.v5ai.nb.common.core.platform.entity.AuditLogDTO;
//import xin.v5ai.nb.common.core.platform.entity.bo.AuditLogListBo;
//import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
//import xin.v5ai.nb.platform.domain.AuditLog;
//import xin.v5ai.nb.platform.domain.vo.AuditLogVo;
//import xin.v5ai.nb.platform.mapper.AuditLogMapper;
//
//import java.time.OffsetDateTime;
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class AuditLogRecorderServiceImpl implements AuditLogRecorderService {
//
//    private final AuditLogMapper mapper;
//
//    @Override
//    public void record(Long tenantId, String actor, String action,
//                       String targetType, String targetId, String detail, String ip) {
//        var entity = new AuditLog();
//        entity.setTenantId(tenantId);
//        entity.setActor(actor);
//        entity.setAction(action);
//        entity.setTargetType(targetType);
//        entity.setTargetId(targetId);
//        entity.setDetail(detail);
//        entity.setIp(ip);
//        entity.setCreatedAt(OffsetDateTime.now());
//        mapper.insert(entity);
//    }
////
////    @Override
////    public List<AuditLogDTO> search(String actor, String action, int limit) {
////        return mapper.search(actor, action, limit).stream().map(this::toDomain).toList();
////    }
//
//    @Override
//    public PageResult<AuditLogDTO> queryPageList(AuditLogListBo bo, PageQuery pageQuery) {
//        LambdaQueryWrapper<AuditLog> lqw = buildQueryWrapper(bo);
//        Page<AuditLogVo> result = mapper.selectVoPage(pageQuery.build(), lqw);
//        return PageResult.build(getRecords(result), result.getTotal());
//    }
//
////    private AuditLogDTO toDomain(AuditLog entity) {
////        return new AuditLogDTO(entity.getId(), entity.getTenantId(), entity.getActor(), entity.getAction(),
////                entity.getTargetType(), entity.getTargetId(), entity.getDetail(), entity.getIp(),
////                entity.getCreatedAt());
////    }
//
//    /**
//     * 构造供应商列表查询条件。
//     *
//     * @param bo 供应商筛选条件
//     * @return 包含 providerKey、name、providerType、enabled 等条件的查询包装器
//     */
//    private LambdaQueryWrapper<AuditLog> buildQueryWrapper(AuditLogListBo bo) {
//        return QueryBuilder.lambda(AuditLog.class)
//                .like(ObjectUtil.isNotNull(bo.actor()), AuditLog::getActor, bo.actor())
//                .eq(ObjectUtil.isNotNull(bo.action()), AuditLog::getAction, bo.action())
//                .orderByAsc(AuditLog::getCreatedAt)
//                .build();
//    }
//
//    private List<AuditLogDTO> getRecords(Page<AuditLogVo> result) {
//        List<AuditLogVo> entities = result.getRecords();
//        return entities.stream().map(entity -> new AuditLogDTO(entity.getId(), entity.getTenantId(), entity.getActor(), entity.getAction(),
//                entity.getTargetType(), entity.getTargetId(), entity.getDetail(), entity.getIp(),
//                entity.getCreatedAt())).toList();
//    }
//}
