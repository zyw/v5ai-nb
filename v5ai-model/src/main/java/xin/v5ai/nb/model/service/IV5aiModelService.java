package xin.v5ai.nb.model.service;

import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;

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
public interface IV5aiModelService {
    /**
     * 查询客户端管理列表
     */
    PageResult<V5aiModelVo> queryPageList(ModelBo bo, PageQuery pageQuery);

    /**
     * 查询客户端管理列表
     */
    List<V5aiModelVo> queryList(ModelBo bo);

    /**
     * 新增客户端管理
     */
    Boolean insertByBo(ModelBo bo);

    /**
     * 修改客户端管理
     */
    Boolean updateByBo(ModelBo bo);

    /**
     * 校验并批量删除客户端管理信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
