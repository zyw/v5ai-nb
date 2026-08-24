package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.ModelUsageRecordVo;

public interface IModelUsageService {

    /**
     * 查询客户端管理列表
     */
    PageResult<ModelUsageRecordVo> queryPageList(ModelUsageListBo bo, PageQuery pageQuery);

}
