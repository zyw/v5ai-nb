package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.PlmLoginInfoBo;
import xin.v5ai.nb.platform.domain.vo.PlmLoginInfoVo;

import java.util.List;

/**
 * <p>
 * 系统访问记录 服务类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-24
 */
public interface IPlmLoginInfoService {
    /**
     * 分页查询登录日志列表
     *
     * @param loginInfo 查询条件
     * @param pageQuery 分页参数
     * @return 登录日志分页列表
     */
    PageResult<PlmLoginInfoVo> selectPageLoginInfoList(PlmLoginInfoBo loginInfo, PageQuery pageQuery);

    /**
     * 新增系统登录日志
     *
     * @param bo 访问日志对象
     */
    void insertLoginInfo(PlmLoginInfoBo bo);

    /**
     * 查询系统登录日志集合
     *
     * @param loginInfo 访问日志对象
     * @return 登录记录集合
     */
    List<PlmLoginInfoVo> selectLoginInfoList(PlmLoginInfoBo loginInfo);

    /**
     * 批量删除系统登录日志
     *
     * @param infoIds 需要删除的登录日志ID
     * @return 结果
     */
    int deleteLoginInfoByIds(Long[] infoIds);

    /**
     * 清空系统登录日志
     */
    void cleanLoginInfo();
}
