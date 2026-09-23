package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.core.utils.ip.AddressUtils;
import xin.v5ai.nb.common.log.event.OperLogEvent;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.PlmOperLog;
import xin.v5ai.nb.platform.domain.bo.PlmOperLogBo;
import xin.v5ai.nb.platform.domain.vo.PlmOperLogVo;
import xin.v5ai.nb.platform.mapper.PlmOperLogMapper;
import xin.v5ai.nb.platform.service.IPlmOperLogService;

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
public class PlmOperLogServiceImpl implements IPlmOperLogService {

    private final PlmOperLogMapper mapper;

    /**
     * 操作日志记录
     *
     * @param operLogEvent 操作日志事件
     */
    @Async
    @EventListener
    public void recordOper(OperLogEvent operLogEvent) {
        PlmOperLogBo operLog = MapstructUtils.convert(operLogEvent, PlmOperLogBo.class);
        operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
        insertOperlog(operLog);
    }

    @Override
    public PageResult<PlmOperLogVo> selectPageOperLogList(PlmOperLogBo operLog, PageQuery pageQuery) {
        LambdaQueryWrapper<PlmOperLog> lqw = buildQueryWrapper(operLog);
        if (StringUtils.isBlank(pageQuery.getOrderByColumn())) {
            lqw.orderByDesc(PlmOperLog::getId);
        }
        Page<PlmOperLogVo> page = mapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public void insertOperlog(PlmOperLogBo bo) {
        PlmOperLog operLog = MapstructUtils.convert(bo, PlmOperLog.class);
        operLog.setOperTime(OffsetDateTime.now());
        mapper.insert(operLog);
    }

    @Override
    public List<PlmOperLogVo> selectOperLogList(PlmOperLogBo operLog) {
        LambdaQueryWrapper<PlmOperLog> lqw = buildQueryWrapper(operLog);
        return mapper.selectVoList(lqw.orderByDesc(PlmOperLog::getId));
    }

    @Override
    public int deleteOperLogByIds(Long[] operIds) {
        return mapper.deleteByIds(Arrays.asList(operIds));
    }

    @Override
    public PlmOperLogVo selectOperLogById(Long operId) {
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
    private LambdaQueryWrapper<PlmOperLog> buildQueryWrapper(PlmOperLogBo operLog) {
        Map<String, Object> params = operLog.getParams();
        return QueryBuilder.lambda(PlmOperLog.class)
                .likeIfText(PlmOperLog::getOperIp, operLog.getOperIp())
                .likeIfText(PlmOperLog::getTitle, operLog.getTitle())
                .eq(operLog.getBusinessType() != null && operLog.getBusinessType() > 0,
                        PlmOperLog::getBusinessType, operLog.getBusinessType())
                .func(f -> {
                    if (ArrayUtil.isNotEmpty(operLog.getBusinessTypes())) {
                        f.in(PlmOperLog::getBusinessType, Arrays.asList(operLog.getBusinessTypes()));
                    }
                })
                .eqIfPresent(PlmOperLog::getStatus, operLog.getStatus())
                .likeIfText(PlmOperLog::getOperName, operLog.getOperName())
                .eqIfPresent(PlmOperLog::getUserId, operLog.getUserId())
                .eqIfText(PlmOperLog::getDeviceType, operLog.getDeviceType())
                .likeIfText(PlmOperLog::getBrowser, operLog.getBrowser())
                .likeIfText(PlmOperLog::getOs, operLog.getOs())
                .betweenParams(PlmOperLog::getOperTime, params, "beginTime", "endTime")
                .build();
    }
}
