package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.domain.bo.StoreConnectionTestBo;
import xin.v5ai.nb.rag.domain.bo.StoreInstanceBo;
import xin.v5ai.nb.rag.domain.vo.StoreConnectionTestVo;
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

    /**
     * 切换存储实例默认状态：true 时清除同分类其它默认，false 时仅取消自身默认。
     *
     * @param id        实例主键
     * @param isDefault 是否设为默认
     * @return 是否切换成功
     */
    Boolean updateDefault(Long id, Boolean isDefault);

    /**
     * 连接测试：按未保存的表单值（type + config）验证连通性；编辑态（带 id）合并库中脱敏字段后测试。
     */
    StoreConnectionTestVo testConnection(StoreConnectionTestBo bo);
}
