package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.domain.bo.StoreInstanceBo;
import xin.v5ai.nb.rag.domain.vo.StoreInstanceVo;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 存储实例服务类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
public interface IStoreInstanceService {

    /**
     * 分页查询存储实例列表
     */
    PageResult<StoreInstanceVo> queryPageList(StoreInstanceBo bo, PageQuery pageQuery);

    /**
     * 查询存储实例列表
     */
    List<StoreInstanceVo> queryList(StoreInstanceBo bo);

    /**
     * 查询存储实例详情
     */
    StoreInstanceVo queryById(Long id);

    /**
     * 新增存储实例
     */
    Boolean insertByBo(StoreInstanceBo bo);

    /**
     * 修改存储实例
     */
    Boolean updateByBo(StoreInstanceBo bo);

    /**
     * 校验并批量删除存储实例
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
