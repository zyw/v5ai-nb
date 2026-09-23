package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.PlmClientBo;
import xin.v5ai.nb.platform.domain.vo.PlmClientVo;

import java.util.Collection;
import java.util.List;

/**
 * 系统授权Service接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface IPlmClientService {

    /**
     * 查询客户端管理
     */
    PlmClientVo queryById(Long id);

    /**
     * 查询客户端信息基于客户端id
     */
    PlmClientVo queryByClientId(String clientId);

    /**
     * 查询客户端管理列表
     */
    PageResult<PlmClientVo> queryPageList(PlmClientBo bo, PageQuery pageQuery);

    /**
     * 查询客户端管理列表
     */
    List<PlmClientVo> queryList(PlmClientBo bo);

    /**
     * 新增客户端管理
     */
    Boolean insertByBo(PlmClientBo bo);

    /**
     * 修改客户端管理
     */
    Boolean updateByBo(PlmClientBo bo);

    /**
     * 修改状态
     */
    int updateClientStatus(String clientId, String status);

    /**
     * 校验并批量删除客户端管理信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 校验客户端key是否唯一
     *
     * @param client 客户端信息
     * @return 结果
     */
    boolean checkClickKeyUnique(PlmClientBo client);
}
