package xin.v5ai.nb.platform.service.impl;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.PlmLoginInfoBo;
import xin.v5ai.nb.platform.domain.vo.PlmLoginInfoVo;
import xin.v5ai.nb.platform.service.IPlmLoginInfoService;

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
public class PlmLoginInfoServiceImpl implements IPlmLoginInfoService {

    @Override
    public PageResult<PlmLoginInfoVo> selectPageLoginInfoList(PlmLoginInfoBo loginInfo, PageQuery pageQuery) {
        return null;
    }

    @Override
    public void insertLoginInfo(PlmLoginInfoBo bo) {

    }

    @Override
    public List<PlmLoginInfoVo> selectLoginInfoList(PlmLoginInfoBo loginInfo) {
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
