package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.constant.Constants;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.core.utils.ip.AddressUtils;
import xin.v5ai.nb.common.log.event.LoginInfoEvent;
import xin.v5ai.nb.common.mybatis.core.mapper.LambdaCrudChainWrapper;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.PlmLoginInfo;
import xin.v5ai.nb.platform.domain.bo.PlmLoginInfoBo;
import xin.v5ai.nb.platform.domain.vo.PlmClientVo;
import xin.v5ai.nb.platform.domain.vo.PlmLoginInfoVo;
import xin.v5ai.nb.platform.mapper.PlmLoginInfoMapper;
import xin.v5ai.nb.platform.service.IPlmClientService;
import xin.v5ai.nb.platform.service.IPlmLoginInfoService;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 系统访问记录 服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlmLoginInfoServiceImpl implements IPlmLoginInfoService {

    private final PlmLoginInfoMapper loginInfoMapper;

    private final IPlmClientService clientService;

    /**
     * 记录登录信息
     *
     * @param loginInfoEvent 登录事件
     */
    @Async
    @EventListener
    public void recordLoginInfo(LoginInfoEvent loginInfoEvent) {
        UserAgent userAgent = UserAgentUtil.parse(loginInfoEvent.getUserAgent());
        String ip = loginInfoEvent.getIp();
        // 客户端信息
        String clientId = loginInfoEvent.getClientId();
        PlmClientVo client = null;
        if (StringUtils.isNotBlank(clientId)) {
            client = clientService.queryByClientId(clientId);
        }

        String address = AddressUtils.getRealAddressByIP(ip);
        String s = getBlock(ip) +
                address +
                getBlock(loginInfoEvent.getUsername()) +
                getBlock(loginInfoEvent.getStatus()) +
                getBlock(loginInfoEvent.getMessage());
        // 打印信息到日志
        log.info(s, loginInfoEvent.getArgs());
        // 获取客户端操作系统
        String os = userAgent.getOs().getName();
        // 获取客户端浏览器
        String browser = userAgent.getBrowser().getName();
        // 封装对象
        PlmLoginInfoBo loginInfo = new PlmLoginInfoBo();
        loginInfo.setUserName(loginInfoEvent.getUsername());
        if (ObjectUtil.isNotNull(client)) {
            loginInfo.setClientKey(client.getClientKey());
            loginInfo.setDeviceType(client.getDeviceType());
        }
        loginInfo.setIpaddr(ip);
        loginInfo.setLoginLocation(address);
        loginInfo.setBrowser(browser);
        loginInfo.setOs(os);
        loginInfo.setMsg(loginInfoEvent.getMessage());
        // 日志状态
        if (StringUtils.equalsAny(loginInfoEvent.getStatus(), Constants.LOGIN_SUCCESS, Constants.LOGOUT, Constants.REGISTER)) {
            loginInfo.setStatus(Constants.SUCCESS);
        } else if (Constants.LOGIN_FAIL.equals(loginInfoEvent.getStatus())) {
            loginInfo.setStatus(Constants.FAIL);
        }
        // 插入数据
        insertLoginInfo(loginInfo);
    }


    @Override
    public PageResult<PlmLoginInfoVo> selectPageLoginInfoList(PlmLoginInfoBo loginInfo, PageQuery pageQuery) {
        LambdaCrudChainWrapper<PlmLoginInfo, PlmLoginInfoVo> lqw = buildQueryWrapper(loginInfo);
        if (StringUtils.isBlank(pageQuery.getOrderByColumn())) {
            lqw.orderByDesc(PlmLoginInfo::getId);
        }
        Page<PlmLoginInfoVo> page = loginInfoMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public void insertLoginInfo(PlmLoginInfoBo bo) {
        PlmLoginInfo loginInfo = MapstructUtils.convert(bo, PlmLoginInfo.class);
        loginInfo.setLoginTime(OffsetDateTime.now());
        loginInfoMapper.insert(loginInfo);
    }

    @Override
    public List<PlmLoginInfoVo> selectLoginInfoList(PlmLoginInfoBo loginInfo) {
        return loginInfoMapper.selectVoList(buildQueryWrapper(loginInfo)
                .orderByDesc(PlmLoginInfo::getId));
    }

    @Override
    public int deleteLoginInfoByIds(Long[] infoIds) {
        return loginInfoMapper.deleteByIds(Arrays.asList(infoIds));
    }

    @Override
    public void cleanLoginInfo() {
        loginInfoMapper.lambda().delete();
    }

    /**
     * 将日志片段包装为统一的方括号格式。
     *
     * @param msg 日志片段内容
     * @return 包装后的日志片段字符串
     */
    private String getBlock(Object msg) {
        if (msg == null) {
            msg = "";
        }
        return "[" + msg + "]";
    }

    /**
     * 构造登录日志列表查询条件。
     *
     * @param loginInfo 登录日志筛选条件
     * @return 登录日志查询包装器
     */
    private LambdaCrudChainWrapper<PlmLoginInfo, PlmLoginInfoVo> buildQueryWrapper(PlmLoginInfoBo loginInfo) {
        Map<String, Object> params = loginInfo.getParams();
        return loginInfoMapper.lambda()
                .likeIfText(PlmLoginInfo::getIpaddr, loginInfo.getIpaddr())
                .eqIfText(PlmLoginInfo::getStatus, loginInfo.getStatus())
                .likeIfText(PlmLoginInfo::getUserName, loginInfo.getUserName())
                .betweenParams(PlmLoginInfo::getLoginTime, params, "beginTime", "endTime");
    }
}
