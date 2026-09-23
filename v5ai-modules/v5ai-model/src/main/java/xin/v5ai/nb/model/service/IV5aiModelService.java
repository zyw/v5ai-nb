package xin.v5ai.nb.model.service;

import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;

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
     * 查询客户端管理下拉列表
     * @param bo 查询条件
     * @return 下拉列表
     */
    List<OptionDTO> queryOptionList(ModelBo bo);

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
    Boolean deleteWithValidById(Long id, Boolean isValid);

    /**
     * 切换模型启用/停用（专用端点语义）。
     * <p>幂等：状态未变化直接返回 true。启用→停用需通过引用校验
     * （被 AgentDTO 或知识库 embedding/rerank 引用时抛 409）；停用默认模型时自动清除其默认标记。</p>
     *
     * @param id      模型 ID
     * @param enabled 目标启用状态，不能为 null
     */
    Boolean updateEnabled(Long id, Boolean enabled);

    /**
     * 切换模型默认标记（专用端点语义）。
     * <p>同 {@code model_type} 至多一个默认：设为默认时清除同类型其它模型的默认；
     * 设为默认要求模型已启用（否则 409）。{@code isDefault} 缺省视为取消默认。</p>
     *
     * @param id        模型 ID
     * @param isDefault 是否设为同类型默认
     */
    Boolean updateDefault(Long id, Boolean isDefault);

    /**
     * 测试模型连接
     */
    TestModelConnectionVo testConnection(Long modelId);
}
