package xin.v5ai.nb.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.AgentVersion;
import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVersionVo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.agent.mapper.AgentMapper;
import xin.v5ai.nb.agent.mapper.AgentVersionMapper;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceImplTest {

    private final List<Agent> agents = new ArrayList<>();
    private final List<AgentVersion> versions = new ArrayList<>();
    private final List<String> deletedKeys = new ArrayList<>();
    private final AtomicLong agentIds = new AtomicLong();
    private final AtomicLong versionIds = new AtomicLong();

    private AgentMapper agentMapper;
    private AgentVersionMapper versionMapper;
    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        agentMapper = mock(AgentMapper.class);
        versionMapper = mock(AgentVersionMapper.class);

        when(agentMapper.selectByAgentKey(anyString())).thenAnswer(inv -> agents.stream()
                .filter(agent -> agent.getAgentKey().equals(inv.getArgument(0)))
                .findFirst().orElse(null));
        when(agentMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(agents));
        when(agentMapper.selectList(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<Agent> page = inv.getArgument(0);
            page.setTotal(agents.size());
            return new ArrayList<>(agents);
        });
        doAnswer(inv -> {
            var agent = inv.getArgument(0, Agent.class);
            agent.setId(agentIds.incrementAndGet());
            agents.add(agent);
            return 1;
        }).when(agentMapper).insert(any(Agent.class));
        doAnswer(inv -> {
            Agent update = inv.getArgument(0, Agent.class);
            agents.stream().filter(agent -> agent.getId().equals(update.getId()))
                    .forEach(agent -> merge(update, agent));
            return 1;
        }).when(agentMapper).updateById(any(Agent.class));

        when(versionMapper.nextVersion(anyString())).thenAnswer(inv -> versions.stream()
                .filter(v -> v.getAgentKey().equals(inv.getArgument(0)))
                .mapToLong(AgentVersion::getVersion).max().orElse(0L) + 1);
        when(versionMapper.selectVersionsByAgentKey(anyString())).thenAnswer(inv -> versions.stream()
                .filter(v -> v.getAgentKey().equals(inv.getArgument(0)))
                .sorted((a, b) -> Long.compare(b.getVersion(), a.getVersion()))
                .toList());
        doAnswer(inv -> {
            var version = inv.getArgument(0, AgentVersion.class);
            version.setId(versionIds.incrementAndGet());
            versions.add(version);
            return 1;
        }).when(versionMapper).insert(any(AgentVersion.class));

        service = new AgentServiceImpl(agentMapper, versionMapper, modelId -> true, deletedKeys::add);
    }

    @Test
    void createsDraftAgentWithModel() {
        var vo = service.createAgent(bo("demo", "Demo", 7L));

        assertThat(vo.getAgentKey()).isEqualTo("demo");
        assertThat(vo.getStatus()).isEqualTo("DRAFT");
        assertThat(agents).hasSize(1);
    }

    @Test
    void createAgentCarriesDisplayAndFeatureFields() {
        var bo = bo("demo", "Demo", 7L);
        bo.setDescription("desc");
        bo.setSystemPrompt("prompt");
        bo.setAvatar("https://example.com/avatar.png");
        bo.setGreeting("你好");
        bo.setPresetQuestions("[\"q1\"]");
        bo.setMemoryEnabled(true);
        bo.setMcpEnabled(true);
        bo.setSkillEnabled(false);
        bo.setWebSearchEnabled(true);
        bo.setRagEnabled(false);

        var vo = service.createAgent(bo);

        assertThat(vo.getAvatar()).isEqualTo("https://example.com/avatar.png");
        assertThat(vo.getGreeting()).isEqualTo("你好");
        assertThat(vo.getPresetQuestions()).isEqualTo("[\"q1\"]");
        assertThat(vo.getMemoryEnabled()).isTrue();
        assertThat(vo.getMcpEnabled()).isTrue();
        assertThat(vo.getSkillEnabled()).isFalse();
        assertThat(vo.getWebSearchEnabled()).isTrue();
        assertThat(vo.getRagEnabled()).isFalse();
    }

    @Test
    void duplicateAgentKeyIsRejected() {
        service.createAgent(bo("demo", "Demo", 7L));

        assertThatThrownBy(() -> service.createAgent(bo("demo", "Other", 8L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createAgentWithoutModelIsRejected() {
        assertThatThrownBy(() -> service.createAgent(bo("demo", "Demo", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent key, name and model are required");
    }

    @Test
    void createAgentWithoutKeyOrNameIsRejected() {
        assertThatThrownBy(() -> service.createAgent(bo(null, "Demo", 7L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createAgent(bo("demo", null, 7L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryListAndQueryPageListReturnAllAgents() {
        service.createAgent(bo("demo", "Demo", 7L));

        assertThat(service.queryList(new AgentBo())).extracting(AgentVo::getAgentKey).containsExactly("demo");

        var page = service.queryPageList(new AgentBo(), new PageQuery());
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRows()).extracting(AgentVo::getAgentKey).containsExactly("demo");
    }

    @Test
    void getAgentReturnsVo() {
        service.createAgent(bo("demo", "Demo", 7L));

        var vo = service.getAgent("demo");

        assertThat(vo.getName()).isEqualTo("Demo");
        assertThatThrownBy(() -> service.getAgent("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void updateAgentMergesUnspecifiedFieldsAndKeepsStatus() {
        service.createAgent(bo("demo", "Demo", 7L));
        var update = new AgentBo();
        update.setName("Demo v2");
        update.setDescription("new description");

        var vo = service.updateAgent("demo", update);

        assertThat(vo.getName()).isEqualTo("Demo v2");
        assertThat(vo.getDescription()).isEqualTo("new description");
        assertThat(vo.getModelId()).isEqualTo(7L);
        assertThat(vo.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void updateAgentKeepsUnspecifiedFeatureFlags() {
        service.createAgent(bo("demo", "Demo", 7L));
        var update = new AgentBo();
        update.setName("Demo v2");
        update.setGreeting("欢迎语");
        update.setRagEnabled(true);

        var vo = service.updateAgent("demo", update);

        assertThat(vo.getGreeting()).isEqualTo("欢迎语");
        assertThat(vo.getRagEnabled()).isTrue();
        assertThat(vo.getMemoryEnabled()).isFalse();
        assertThat(vo.getMcpEnabled()).isFalse();
    }

    @Test
    void disableAgentSetsDisabledStatus() {
        service.createAgent(bo("demo", "Demo", 7L));

        service.disable("demo");

        assertThat(agents.get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void deleteAgentDelegatesCascadeDeletion() {
        service.createAgent(bo("demo", "Demo", 7L));

        service.delete("demo");

        assertThat(deletedKeys).containsExactly("demo");
    }

    @Test
    void publishRejectsAgentWithoutEnabledModel() {
        var agent = new Agent();
        agent.setAgentKey("demo");
        agent.setName("Demo");
        agent.setStatus("DRAFT");
        agent.setModelId(null);
        agents.add(agent);
        var strictService = new AgentServiceImpl(agentMapper, versionMapper, modelId -> false, deletedKeys::add);

        assertThatThrownBy(() -> strictService.publish("demo", "release"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("enabled model");
    }

    @Test
    void publishCreatesSnapshotAndPublishesVersion() {
        service.createAgent(bo("demo", "Demo", 7L));

        service.publish("demo", "first release");

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo(1L);
        assertThat(versions.get(0).getSnapshotJson())
                .contains("\"agentKey\":\"demo\"")
                .contains("\"modelId\":7");
        assertThat(agents.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(agents.get(0).getPublishedVersion()).isEqualTo(1L);
    }

    @Test
    void listVersionsReturnsNewestFirst() {
        service.createAgent(bo("demo", "Demo", 7L));
        service.publish("demo", "v1");
        service.publish("demo", "v2");

        var vos = service.listVersions("demo");

        assertThat(vos).extracting(AgentVersionVo::getVersion).containsExactly(2L, 1L);
    }

    private static AgentBo bo(String agentKey, String name, Long modelId) {
        var bo = new AgentBo();
        bo.setAgentKey(agentKey);
        bo.setName(name);
        bo.setModelId(modelId);
        return bo;
    }

    private static void merge(Agent update, Agent target) {
        if (update.getAgentKey() != null) {
            target.setAgentKey(update.getAgentKey());
        }
        if (update.getName() != null) {
            target.setName(update.getName());
        }
        if (update.getDescription() != null) {
            target.setDescription(update.getDescription());
        }
        if (update.getStatus() != null) {
            target.setStatus(update.getStatus());
        }
        if (update.getModelId() != null) {
            target.setModelId(update.getModelId());
        }
        if (update.getPublishedVersion() != null) {
            target.setPublishedVersion(update.getPublishedVersion());
        }
        if (update.getSystemPrompt() != null) {
            target.setSystemPrompt(update.getSystemPrompt());
        }
        if (update.getAvatar() != null) {
            target.setAvatar(update.getAvatar());
        }
        if (update.getGreeting() != null) {
            target.setGreeting(update.getGreeting());
        }
        if (update.getPresetQuestions() != null) {
            target.setPresetQuestions(update.getPresetQuestions());
        }
        if (update.getMemoryEnabled() != null) {
            target.setMemoryEnabled(update.getMemoryEnabled());
        }
        if (update.getMcpEnabled() != null) {
            target.setMcpEnabled(update.getMcpEnabled());
        }
        if (update.getSkillEnabled() != null) {
            target.setSkillEnabled(update.getSkillEnabled());
        }
        if (update.getWebSearchEnabled() != null) {
            target.setWebSearchEnabled(update.getWebSearchEnabled());
        }
        if (update.getRagEnabled() != null) {
            target.setRagEnabled(update.getRagEnabled());
        }
    }
}
