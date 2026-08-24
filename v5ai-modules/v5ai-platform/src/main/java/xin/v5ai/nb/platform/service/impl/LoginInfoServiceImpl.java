package xin.v5ai.nb.platform.service.impl;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.LoginInfoBo;
import xin.v5ai.nb.platform.domain.vo.LoginInfoVo;
import xin.v5ai.nb.platform.service.ILoginInfoService;

import java.util.List;

/**
 * <p>
 * 系统访问记录 服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-24
 */
@Service
public class LoginInfoServiceImpl implements ILoginInfoService {

    @Override
    public PageResult<LoginInfoVo> selectPageLoginInfoList(LoginInfoBo loginInfo, PageQuery pageQuery) {
        return null;
    }

    @Override
    public void insertLoginInfo(LoginInfoBo bo) {

    }

    @Override
    public List<LoginInfoVo> selectLoginInfoList(LoginInfoBo loginInfo) {
        return List.of();
    }

    @Override
    public int deleteLoginInfoByIds(Long[] infoIds) {
        return 0;
    }

    @Override
    public void cleanLoginInfo() {

    }
}
