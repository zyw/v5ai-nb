package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.log.event.OperLogEvent;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.OperLog;
import xin.v5ai.nb.platform.domain.bo.OperLogBo;
import xin.v5ai.nb.platform.domain.vo.OperLogVo;
import xin.v5ai.nb.platform.mapper.OperLogMapper;
import xin.v5ai.nb.platform.service.IOperLogService;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 操作日志记录 服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-24
 */
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements IOperLogService {

    private final OperLogMapper mapper;

    /**
     * 操作日志记录
     *
     * @param operLogEvent 操作日志事件
     */
    @Async
    @EventListener
    public void recordOper(OperLogEvent operLogEvent) {
        OperLogBo operLog = MapstructUtils.convert(operLogEvent, OperLogBo.class);
        // TODO 远程查询操作地点
//        operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
        insertOperlog(operLog);
    }

    @Override
    public PageResult<OperLogVo> selectPageOperLogList(OperLogBo operLog, PageQuery pageQuery) {
        LambdaQueryWrapper<OperLog> lqw = buildQueryWrapper(operLog);
        if (StringUtils.isBlank(pageQuery.getOrderByColumn())) {
            lqw.orderByDesc(OperLog::getId);
        }
        Page<OperLogVo> page = mapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public void insertOperlog(OperLogBo bo) {
        OperLog operLog = MapstructUtils.convert(bo, OperLog.class);
        operLog.setOperTime(OffsetDateTime.now());
        mapper.insert(operLog);
    }

    @Override
    public List<OperLogVo> selectOperLogList(OperLogBo operLog) {
        LambdaQueryWrapper<OperLog> lqw = buildQueryWrapper(operLog);
        return mapper.selectVoList(lqw.orderByDesc(OperLog::getId));
    }

    @Override
    public int deleteOperLogByIds(Long[] operIds) {
        return mapper.deleteByIds(Arrays.asList(operIds));
    }

    @Override
    public OperLogVo selectOperLogById(Long operId) {
        return mapper.selectVoById(operId);
    }

    @Override
    public void cleanOperLog() {
        mapper.lambda().delete();
    }

    /**
     * 构造操作日志查询条件。
     *
     * @param operLog 操作日志筛选条件
     * @return 包含业务类型、状态、操作人和时间区间的查询包装器
     */
    private LambdaQueryWrapper<OperLog> buildQueryWrapper(OperLogBo operLog) {
        Map<String, Object> params = operLog.getParams();
        return QueryBuilder.lambda(OperLog.class)
                .likeIfText(OperLog::getOperIp, operLog.getOperIp())
                .likeIfText(OperLog::getTitle, operLog.getTitle())
                .eq(operLog.getBusinessType() != null && operLog.getBusinessType() > 0,
                        OperLog::getBusinessType, operLog.getBusinessType())
                .func(f -> {
                    if (ArrayUtil.isNotEmpty(operLog.getBusinessTypes())) {
                        f.in(OperLog::getBusinessType, Arrays.asList(operLog.getBusinessTypes()));
                    }
                })
                .eqIfPresent(OperLog::getStatus, operLog.getStatus())
                .likeIfText(OperLog::getOperName, operLog.getOperName())
                .eqIfPresent(OperLog::getUserId, operLog.getUserId())
                .eqIfText(OperLog::getDeviceType, operLog.getDeviceType())
                .likeIfText(OperLog::getBrowser, operLog.getBrowser())
                .likeIfText(OperLog::getOs, operLog.getOs())
                .betweenParams(OperLog::getOperTime, params, "beginTime", "endTime")
                .build();
    }
}
