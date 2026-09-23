package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.ModelUsagePageVo;

public interface IV5aiModelUsageService {

    /**
     * 查询客户端管理列表
     */
    PageResult<ModelUsagePageVo> queryPageList(ModelUsageListBo bo, PageQuery pageQuery);

}
