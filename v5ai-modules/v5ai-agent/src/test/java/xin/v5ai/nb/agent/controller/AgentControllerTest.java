package xin.v5ai.nb.agent.controller;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.controller.vo.GenerateAgentConfigRequest;
import xin.v5ai.nb.agent.controller.vo.PublishAgentRequest;
import xin.v5ai.nb.agent.core.GeneratedAgentConfig;
import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.agent.service.IAgentService;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    void createsAgentAndReturnsVo() {
        var service = mock(IAgentService.class);
        var vo = new AgentVo();
        vo.setAgentKey("demo");
        vo.setName("Demo");
        when(service.createAgent(any(AgentBo.class))).thenReturn(vo);
        var controller = new AgentController(service, (modelId, description) ->
                new GeneratedAgentConfig("generated", "gen desc", "hi", List.of("q1"), "prompt"));

        var created = controller.createAgent(new AgentBo()).getData();

        assertThat(created.getAgentKey()).isEqualTo("demo");
        assertThat(created.getName()).isEqualTo("Demo");
    }

    @Test
    void listsAgentsPaginated() {
        var service = mock(IAgentService.class);
        var vo = new AgentVo();
        vo.setAgentKey("demo");
        when(service.queryPageList(any(AgentBo.class), any(PageQuery.class)))
                .thenReturn(PageResult.build(List.of(vo), 1));
        var controller = new AgentController(service, (modelId, description) -> null);

        var page = controller.listAgents(new AgentBo(), new PageQuery()).getData();

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRows()).extracting(AgentVo::getAgentKey).containsExactly("demo");
    }

    @Test
    void updatesDisablesAndDeletesAgent() {
        var service = mock(IAgentService.class);
        var controller = new AgentController(service, (modelId, description) -> null);

        controller.updateAgent("demo", new AgentBo());
        controller.disableAgent("demo");
        controller.deleteAgent("demo");
        controller.publishAgent("demo", new PublishAgentRequest("release"));

        verify(service).updateAgent("demo", new AgentBo());
        verify(service).disable("demo");
        verify(service).delete("demo");
        verify(service).publish("demo", "release");
    }

    @Test
    void generatesAgentConfigFromDescription() {
        var service = mock(IAgentService.class);
        var controller = new AgentController(service, (modelId, description) ->
                new GeneratedAgentConfig("智能客服", "7x24 客服", "您好，有什么可以帮您？",
                        List.of("怎么退款？", "如何联系人工？"), "You are a customer service agent."));

        var generated = controller.generateAgentConfig(new GenerateAgentConfigRequest(7L, "帮我设计一个智能客服")).getData();

        assertThat(generated.name()).isEqualTo("智能客服");
        assertThat(generated.description()).isEqualTo("7x24 客服");
        assertThat(generated.greeting()).isEqualTo("您好，有什么可以帮您？");
        assertThat(generated.presetQuestions()).containsExactly("怎么退款？", "如何联系人工？");
        assertThat(generated.systemPrompt()).isEqualTo("You are a customer service agent.");
    }
}
