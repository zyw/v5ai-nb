package xin.v5ai.nb.agent.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.agent.controller.vo.GenerateAgentConfigRequest;
import xin.v5ai.nb.agent.controller.vo.GenerateAgentConfigResponse;
import xin.v5ai.nb.agent.controller.vo.PublishAgentRequest;
import xin.v5ai.nb.agent.core.AgentConfigGenerator;
import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVersionVo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.agent.service.IAgentService;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;

import java.util.List;

/**
 * AgentDTO 管理 API：新建（含向导生成配置）、分页列表、更新、禁用、删除、版本列表与发布。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agents")
public class AgentController extends BaseController {

    private final IAgentService agentService;
    private final AgentConfigGenerator agentConfigGenerator;

    /**
     * 新建智能体向导：根据描述调用模型生成配置（名称/描述/欢迎语/预设问题/系统提示词）。
     */
    @PostMapping("/generate")
    @Log(title = "生成智能体配置", businessType = BusinessType.GENCODE)
    public R<GenerateAgentConfigResponse> generateAgentConfig(@RequestBody GenerateAgentConfigRequest request) {
        return R.ok(GenerateAgentConfigResponse.from(
                agentConfigGenerator.generate(request.modelId(), request.description())));
    }

    /**
     * 新建智能体。
     * @param bo 新建智能体的请求参数
     * @return 新建智能体的响应结果
     */
    @PostMapping
    @Log(title = "新建智能体", businessType = BusinessType.INSERT)
    public R<AgentVo> createAgent(@Validated(AddGroup.class) @RequestBody AgentBo bo) {
        var created = agentService.createAgent(bo);
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<AgentVo>> listAgents(AgentBo bo, PageQuery pageQuery) {
        return R.ok(agentService.queryPageList(bo, pageQuery));
    }

    @GetMapping("/{agentKey}")
    public R<AgentVo> getAgent(@PathVariable("agentKey") String agentKey) {
        return R.ok(agentService.getAgent(agentKey));
    }

    /**
     * 更新智能体。
     * @param agentKey 智能体键
     * @param bo 更新智能体的请求参数
     * @return 更新智能体的响应结果
     */
    @PutMapping("/{agentKey}")
    @Log(title = "更新智能体", businessType = BusinessType.UPDATE)
    public R<AgentVo> updateAgent(@PathVariable("agentKey") String agentKey,
                                  @RequestBody AgentBo bo) {
        return R.ok(agentService.updateAgent(agentKey, bo));
    }

    /**
     * 禁用智能体。
     * @param agentKey 智能体键
     * @return 禁用智能体的响应结果
     */
    @PostMapping("/{agentKey}/disable")
    @Log(title = "禁用智能体", businessType = BusinessType.DISABLE)
    public R<Void> disableAgent(@PathVariable("agentKey") String agentKey) {
        agentService.disable(agentKey);
        return R.ok();
    }
    /**
     * 删除智能体。
     * @param agentKey 智能体键
     * @return 删除智能体的响应结果
     */
    @DeleteMapping("/{agentKey}")
    @Log(title = "删除智能体", businessType = BusinessType.DELETE)
    public R<Void> deleteAgent(@PathVariable("agentKey") String agentKey) {
        agentService.delete(agentKey);
        return R.ok();
    }
    /**
     * 智能体版本列表。
     * @param agentKey 智能体键
     * @param pageQuery 分页查询参数
     * @return 智能体版本列表的响应结果
     */

    @GetMapping("/{agentKey}/versions")
    public R<PageResult<AgentVersionVo>> listVersions(@PathVariable("agentKey") String agentKey, PageQuery pageQuery) {
        List<AgentVersionVo> list = agentService.listVersions(agentKey);
        return R.ok(PageResult.build(list, (long) list.size()));
    }

    /**
     * 发布智能体。
     * @param agentKey 智能体键
     * @param request 发布智能体的请求参数
     * @return 发布智能体的响应结果
     */
    @PostMapping("/{agentKey}/publish")
    @Log(title = "发布智能体", businessType = BusinessType.PUBLISH)
    public R<Void> publishAgent(@PathVariable("agentKey") String agentKey,
                                @RequestBody PublishAgentRequest request) {
        agentService.publish(agentKey, request == null ? null : request.description());
        return R.ok();
    }
}
