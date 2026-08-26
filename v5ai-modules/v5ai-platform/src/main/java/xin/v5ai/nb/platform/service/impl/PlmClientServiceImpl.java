package xin.v5ai.nb.platform.service.impl;

import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.domain.bo.SysClientBo;
import xin.v5ai.nb.domain.vo.SysClientVo;
import xin.v5ai.nb.domain.SysClient;
import xin.v5ai.nb.mapper.SysClientMapper;
import xin.v5ai.nb.service.ISysClientService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 系统授权Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysClientServiceImpl implements ISysClientService {

    private final SysClientMapper baseMapper;

    /**
     * 查询系统授权
     *
     * @param id 主键
     * @return 系统授权
     */
    @Override
    public SysClientVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询系统授权列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 系统授权分页列表
     */
    @Override
    public TableDataInfo queryPageList(SysClientBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        Page result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的系统授权列表
     *
     * @param bo 查询条件
     * @return 系统授权列表
     */
    @Override
    public List queryList(SysClientBo bo) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper buildQueryWrapper(SysClientBo bo) {
        Map params = bo.getParams();
        LambdaQueryWrapper lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(SysClient::getId);
        lqw.eq(bo.getClientId() != null, SysClient::getClientId, bo.getClientId());
        lqw.eq(bo.getClientKey() != null, SysClient::getClientKey, bo.getClientKey());
        lqw.eq(bo.getClientSecret() != null, SysClient::getClientSecret, bo.getClientSecret());
        lqw.eq(bo.getGrantType() != null, SysClient::getGrantType, bo.getGrantType());
        lqw.eq(bo.getDeviceType() != null, SysClient::getDeviceType, bo.getDeviceType());
        lqw.eq(bo.getActiveTimeout() != null, SysClient::getActiveTimeout, bo.getActiveTimeout());
        lqw.eq(bo.getTimeout() != null, SysClient::getTimeout, bo.getTimeout());
        lqw.eq(bo.getStatus() != null, SysClient::getStatus, bo.getStatus());
        return lqw;
    }

    /**
     * 新增系统授权
     *
     * @param bo 系统授权
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(SysClientBo bo) {
        SysClient add = MapstructUtils.convert(bo, SysClient.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改系统授权
     *
     * @param bo 系统授权
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(SysClientBo bo) {
        SysClient update = MapstructUtils.convert(bo, SysClient.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysClient entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除系统授权信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
