package xin.v5ai.nb.platform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.V5aiApiKeyBo;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeyVo;
import xin.v5ai.nb.platform.service.IV5aiApiKeyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AgentApiKeyController extends BaseController {

    private final IV5aiApiKeyService service;

    @PostMapping("/agents/{agentKey}/api-keys")
    @Log(title = "生成应用API密钥", businessType = BusinessType.INSERT)
    public R<Void> add(@PathVariable("agentKey") String agentKey) {
        return toAjax(service.insertApiKey(agentKey));
    }

    @GetMapping("/api-keys")
    public R<PageResult<V5aiApiKeyVo>> list(V5aiApiKeyBo bo, PageQuery pageQuery) {
        return R.ok(service.selectPageList(bo, pageQuery));
    }

    @DeleteMapping("/agents/{agentKey}/api-keys")
    @Log(title = "删除应用API密钥", businessType = BusinessType.DELETE)
    public R<Void> deleteApiKey(@PathVariable("agentKey") String agentKey) {
        return toAjax(service.delete(agentKey));
    }
}
