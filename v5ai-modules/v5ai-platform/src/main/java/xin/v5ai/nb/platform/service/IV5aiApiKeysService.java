package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.V5aiApiKeysBo;
import xin.v5ai.nb.platform.domain.vo.ApiKeysRespVo;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeysVo;

import java.util.List;

/**
 * API Key 管理服务：Key 归属创建用户，可绑定多个「已发布」Agent。
 */
public interface IV5aiApiKeysService {

    /**
     * 分页查询指定用户创建的 Key。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @param userId    当前登录用户（Key 归属者）
     * @return Key 分页列表（含可访问 Agent 列表）
     */
    PageResult<V5aiApiKeysVo> selectPageList(V5aiApiKeysBo bo, PageQuery pageQuery, Long userId);

    /**
     * 新建 Key：校验绑定的 Agent 均已发布，生成明文（仅本次返回）与 tracking id，落库并建立绑定。
     *
     * @param userId    当前登录用户（Key 归属者）
     * @param name      Key 名称
     * @param agentKeys 可访问的 agentKey 列表（至少一个，且必须为 PUBLISHED）
     * @return 含明文 API Key 的响应
     */
    ApiKeysRespVo insertApiKey(Long userId, String name, List<String> agentKeys);

    /**
     * 修改名称/可访问 Agent/启用状态（Agent 绑定为覆盖式重绑）。
     *
     * @param id        Key 主键（必须属于 userId）
     * @param userId    当前登录用户
     * @param name      Key 名称
     * @param agentKeys 可访问的 agentKey 列表（至少一个，且必须为 PUBLISHED）
     * @param enabled   启用状态；为 null 表示保持不变
     */
    void updateApiKey(Long id, Long userId, String name, List<String> agentKeys, Boolean enabled);

    /**
     * 启用/停用 Key。
     *
     * @param id      Key 主键（必须属于 userId）
     * @param userId  当前登录用户
     * @param enabled 启用状态
     */
    void changeEnabled(Long id, Long userId, Boolean enabled);

    /**
     * 批量删除 Key（绑定关系由外键级联删除）。任一 id 不属于 userId 时整体拒绝，不做部分删除。
     *
     * @param ids    Key 主键列表（每个都必须属于 userId）
     * @param userId 当前登录用户
     */
    void deleteApiKeys(List<Long> ids, Long userId);
}