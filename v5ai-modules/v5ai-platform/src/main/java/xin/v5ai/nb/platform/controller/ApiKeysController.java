package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.V5aiApiKeysBo;
import xin.v5ai.nb.platform.domain.vo.ApiKeysRespVo;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeysVo;
import xin.v5ai.nb.platform.service.IV5aiApiKeysService;

import java.util.List;

/**
 * API Key 管理：新建/改名/重绑可访问 Agent/启停/删除。
 *
 * <p>Key 归属创建用户，列表与写操作都只作用于当前登录用户自己的 Key；
 * 可访问的 Agent 只能是已发布（PUBLISHED）状态，服务端强制校验。</p>
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/api-keys")
public class ApiKeysController extends BaseController {

    private final IV5aiApiKeysService apiKeyService;

    /**
     * 新建 Key 的请求体：名称 + 可访问的已发布 Agent（多选）。
     */
    public record ApiKeyCreateRequest(
            @NotBlank(message = "Key 名称不能为空") String name,
            @NotEmpty(message = "请至少选择一个可访问的 Agent") List<String> agentKeys) {
    }

    /**
     * 修改 Key 的请求体：改名 + 覆盖式重绑 Agent，可选启停。
     */
    public record ApiKeyUpdateRequest(
            @NotBlank(message = "Key 名称不能为空") String name,
            @NotEmpty(message = "请至少选择一个可访问的 Agent") List<String> agentKeys,
            Boolean enabled) {
    }

    /**
     * 启停 Key 的请求体。
     */
    public record ApiKeyEnabledRequest(@NotNull(message = "启用状态不能为空") Boolean enabled) {
    }

    /**
     * 分页查询当前登录用户创建的 API Key。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return Key 分页列表
     */
    @SaCheckPermission("apiKeys:keys:list")
    @GetMapping
    public R<PageResult<V5aiApiKeysVo>> list(V5aiApiKeysBo bo, PageQuery pageQuery) {
        return R.ok(apiKeyService.selectPageList(bo, pageQuery, LoginHelper.getUserId()));
    }

    /**
     * 新建 API Key 并绑定可访问的已发布 Agent。
     *
     * @param request 名称与 Agent 列表
     * @return 含明文 API Key 的响应（明文仅此一次返回）
     */
    @SaCheckPermission("apiKeys:keys:add")
    @Log(title = "API Key 管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ApiKeysRespVo> add(@Validated @RequestBody ApiKeyCreateRequest request) {
        return R.ok(apiKeyService.insertApiKey(LoginHelper.getUserId(), request.name(), request.agentKeys()));
    }

    /**
     * 修改 API Key（名称、可访问 Agent、启用状态）。
     *
     * @param id      主键
     * @param request 修改内容
     * @return 操作结果
     */
    @SaCheckPermission("apiKeys:keys:edit")
    @Log(title = "API Key 管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Void> edit(@PathVariable("id") Long id, @Validated @RequestBody ApiKeyUpdateRequest request) {
        apiKeyService.updateApiKey(id, LoginHelper.getUserId(), request.name(), request.agentKeys(), request.enabled());
        return R.ok();
    }

    /**
     * 启用/停用 API Key。
     *
     * @param id      主键
     * @param request 启用状态
     * @return 操作结果
     */
    @SaCheckPermission("apiKeys:keys:edit")
    @Log(title = "API Key 管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/enabled")
    public R<Void> changeEnabled(@PathVariable("id") Long id,
                                 @Validated @RequestBody ApiKeyEnabledRequest request) {
        apiKeyService.changeEnabled(id, LoginHelper.getUserId(), request.enabled());
        return R.ok();
    }

    /**
     * 批量删除 API Key（路径上逗号分隔主键，与「角色管理」的批量删除一致）；
     * 绑定关系一并删除，使用这些 Key 的调用立即失效。
     *
     * @param ids 主键串（一个或多个，逗号分隔）
     * @return 操作结果
     */
    @SaCheckPermission("apiKeys:keys:remove")
    @Log(title = "API Key 管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable("ids") Long[] ids) {
        apiKeyService.deleteApiKeys(List.of(ids), LoginHelper.getUserId());
        return R.ok();
    }
}