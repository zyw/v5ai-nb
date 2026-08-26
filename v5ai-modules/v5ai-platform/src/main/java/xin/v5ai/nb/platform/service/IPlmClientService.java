package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.domain.vo.SysClientVo;
import xin.v5ai.nb.domain.bo.SysClientBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 系统授权Service接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface ISysClientService {

    /**
     * 查询系统授权
     *
     * @param ${pkColumn.javaField} 主键
     * @return 系统授权
     */
    SysClientVo queryById(Long id);

    /**
     * 分页查询系统授权列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 系统授权分页列表
     */
    TableDataInfo queryPageList(SysClientBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的系统授权列表
     *
     * @param bo 查询条件
     * @return 系统授权列表
     */
    List queryList(SysClientBo bo);

    /**
     * 新增系统授权
     *
     * @param bo 系统授权
     * @return 是否新增成功
     */
    Boolean insertByBo(SysClientBo bo);

    /**
     * 修改系统授权
     *
     * @param bo 系统授权
     * @return 是否修改成功
     */
    Boolean updateByBo(SysClientBo bo);

    /**
     * 校验并批量删除系统授权信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection ids, Boolean isValid);
}
