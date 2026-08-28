package xin.v5ai.nb.model.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.model.domain.bo.ModelProviderBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelProviderVo;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
public interface IV5aiModelProviderService {


    /**
     * 查询客户端管理列表
     */
    PageResult<V5aiModelProviderVo> queryPageList(ModelProviderBo bo, PageQuery pageQuery);

    /**
     * 查询客户端管理列表
     */
    List<V5aiModelProviderVo> queryList(ModelProviderBo bo);

    /**
     * 新增客户端管理
     */
    Boolean insertByBo(ModelProviderBo bo);

    /**
     * 修改客户端管理
     */
    Boolean updateByBo(ModelProviderBo bo);

    /**
     * 校验并批量删除客户端管理信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
