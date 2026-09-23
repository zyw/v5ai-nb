package xin.v5ai.nb.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.AgentVersion;
import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVersionVo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.agent.mapper.AgentMapper;
import xin.v5ai.nb.agent.mapper.AgentVersionMapper;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.model.api.ModelCatalogPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceImplTest {

    private final List<Agent> agents = new ArrayList<>();
    private final List<AgentVersion> versions = new ArrayList<>();
    private final List<String> deletedKeys = new ArrayList<>();
    private final AtomicLong agentIds = new AtomicLong();
    private final AtomicLong versionIds = new AtomicLong();

    private AgentMapper agentMapper;
    private AgentVersionMapper versionMapper;
    private ModelCatalogPort modelCatalog;
    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境无 MyBatis 启动流程，手动初始化实体 TableInfo，使 LambdaQueryWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Agent.class);

        agentMapper = mock(AgentMapper.class);
        versionMapper = mock(AgentVersionMapper.class);

        when(agentMapper.selectByAgentKey(anyString())).thenAnswer(inv -> agents.stream()
                .filter(agent -> agent.getAgentKey().equals(inv.getArgument(0)))
                .findFirst().orElse(null));
        // queryList / queryPageList 走的是 BaseMapperPlus 的接口 default 方法 selectVoList / selectVoPage。
        // 这两条 default 实现依赖 MapstructUtils（需要 Spring 容器提供 Converter），纯单测里跑不了；
        // Mockito 又不会执行默认实现，不显式打桩就只会返回 null。这里按真实语义直接返回 VO，
        // 断言仍落在「传下去的 Wrapper」与「返回的记录」上。
        when(agentMapper.selectVoList(any(Wrapper.class)))
                .thenAnswer(inv -> agents.stream().map(AgentServiceImplTest::toVo).toList());
        when(agentMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<?> page = inv.getArgument(0);
            var voPage = new Page<AgentVo>(page.getCurrent(), page.getSize(), agents.size());
            voPage.setRecords(agents.stream().map(AgentServiceImplTest::toVo).toList());
            return voPage;
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
        // clearSecondaryModel 是接口 default 方法：Mockito 不会执行默认实现，必须显式打桩，
        // 否则「清除」路径在内存态里看不到效果，断言会假绿/假红。
        doAnswer(inv -> {
            Long id = inv.getArgument(0);
            agents.stream().filter(agent -> agent.getId().equals(id))
                    .forEach(agent -> agent.setSecondaryModelId(null));
            return 1;
        }).when(agentMapper).clearSecondaryModel(anyLong());

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

        modelCatalog = mock(ModelCatalogPort.class);
        when(modelCatalog.isEnabledChatModel(anyLong())).thenReturn(true);

        service = new AgentServiceImpl(agentMapper, versionMapper, modelId -> true, deletedKeys::add,
                catalogProvider(modelCatalog));
    }

    /**
     * {@link ObjectProvider} 的最小替身：只需 {@code getIfAvailable()} 的语义。
     *
     * <p>不用 lambda——{@code ObjectProvider} 有多个抽象方法（getObject / getIfAvailable /
     * getIfUnique），只能用 mock 或匿名类。</p>
     */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<ModelCatalogPort> catalogProvider(ModelCatalogPort port) {
        ObjectProvider<ModelCatalogPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        return provider;
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

    /**
     * 列表查询条件：keyword 生成 name/agentKey 的 OR 组，用 MyBatis-Plus like 实现（与数据库无关），
     * keyword 空白时该组不参与；status 仍是精确匹配。
     * mapper 被 mock 掉、不执行 SQL，故断言生成的 Wrapper 片段。
     */
    @Test
    void queryPageListBuildsKeywordOrGroupAndStatusFilter() {
        service.createAgent(bo("demo", "Demo", 7L));
        ArgumentCaptor<LambdaQueryWrapper<Agent>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);

        var byKeyword = new AgentBo();
        byKeyword.setKeyword("DEMO");
        byKeyword.setStatus("DRAFT");
        service.queryPageList(byKeyword, new PageQuery());
        verify(agentMapper).selectVoPage(any(Page.class), captor.capture());

        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("LIKE").contains("name").contains("agent_key").contains("OR");
        assertThat(sql).contains("status");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains("%DEMO%");

        clearInvocations(agentMapper);
        service.queryPageList(new AgentBo(), new PageQuery());
        verify(agentMapper).selectVoPage(any(Page.class), captor.capture());

        String blankSql = captor.getValue().getSqlSegment();
        // keyword 空白时整组不参与：既无 LIKE，也不能残留空括号 "()"（否则生成 `WHERE ()` 非法 SQL）。
        assertThat(blankSql).doesNotContain("LIKE").doesNotContain("()");
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
    void createAgentCarriesSecondaryModelAndCitationsToggle() {
        var bo = bo("demo", "Demo", 7L);
        bo.setSecondaryModelId(9L);
        bo.setShowCitations(false);

        var vo = service.createAgent(bo);

        assertThat(vo.getSecondaryModelId()).isEqualTo(9L);
        assertThat(vo.getShowCitations()).isFalse();
    }

    /** 未提交这两个字段时的存量默认：次要模型为空（回退主模型）、引用照常展示。 */
    @Test
    void createAgentDefaultsSecondaryModelToNullAndCitationsToTrue() {
        var vo = service.createAgent(bo("demo", "Demo", 7L));

        assertThat(vo.getSecondaryModelId()).isNull();
        assertThat(vo.getShowCitations()).isTrue();
    }

    @Test
    void createAgentRejectsUnusableSecondaryModel() {
        when(modelCatalog.isEnabledChatModel(9L)).thenReturn(false);
        var bo = bo("demo", "Demo", 7L);
        bo.setSecondaryModelId(9L);

        assertThatThrownBy(() -> service.createAgent(bo))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("次要模型不可用");
        assertThat(agents).isEmpty();
    }

    /**
     * 取不到 {@link ModelCatalogPort} 实现时跳过校验（与运行时侧对 ModelUsageService 的处理一致）：
     * 校验只是「提前发现配置错误」的增强，缺了它运行期仍有回退主模型的兜底。
     */
    @Test
    void secondaryModelValidationIsSkippedWhenCatalogPortIsAbsent() {
        var serviceWithoutCatalog = new AgentServiceImpl(agentMapper, versionMapper, modelId -> true,
                deletedKeys::add, catalogProvider(null));
        var bo = bo("demo", "Demo", 7L);
        bo.setSecondaryModelId(9L);

        assertThat(serviceWithoutCatalog.createAgent(bo).getSecondaryModelId()).isEqualTo(9L);
    }

    @Test
    void updateAgentRejectsUnusableSecondaryModel() {
        service.createAgent(bo("demo", "Demo", 7L));
        when(modelCatalog.isEnabledChatModel(9L)).thenReturn(false);
        var update = new AgentBo();
        update.setName("Demo v2");
        update.setSecondaryModelId(9L);

        assertThatThrownBy(() -> service.updateAgent("demo", update))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("次要模型不可用");
    }

    @Test
    void updateAgentReplacesSecondaryModelWhenProvided() {
        var created = service.createAgent(bo("demo", "Demo", 7L));
        assertThat(created.getSecondaryModelId()).isNull();
        var update = new AgentBo();
        update.setName("Demo v2");
        update.setSecondaryModelId(9L);

        assertThat(service.updateAgent("demo", update).getSecondaryModelId()).isEqualTo(9L);
        verify(agentMapper, never()).clearSecondaryModel(anyLong());
    }

    /**
     * 次要模型的 null 表示**清除**（回退主模型），而非其它字段的「null 即不改」。
     *
     * <p>清除走 {@code clearSecondaryModel} 单独一条语句：全局 {@code updateStrategy: NOT_NULL}
     * 下 {@code updateById} 写不进 null。这里同时断言「没走 updateById 那条路」。</p>
     */
    @Test
    void updateAgentClearsSecondaryModelWhenSubmittedEmpty() {
        var bo = bo("demo", "Demo", 7L);
        bo.setSecondaryModelId(9L);
        service.createAgent(bo);
        var update = new AgentBo();
        update.setName("Demo v2");
        update.setSecondaryModelId(null);

        var vo = service.updateAgent("demo", update);

        assertThat(vo.getSecondaryModelId()).isNull();
        assertThat(vo.getName()).isEqualTo("Demo v2");
        verify(agentMapper).clearSecondaryModel(agents.get(0).getId());
    }

    /**
     * 取舍的另一面（有意为之）：{@code updateAgent} 的入参是管理端的**整表单**，
     * 因此未带次要模型的调用会清掉已配置的值。当前唯一调用方是
     * {@code AgentController}（表单提交），不存在只改个别字段的内部调用，
     * 故选择「可清除」而不留一个无法回退的死角。若将来出现部分更新调用方，需改为显式标记。
     */
    @Test
    void updateAgentTreatsAbsentSecondaryModelAsClear() {
        var create = bo("demo", "Demo", 7L);
        create.setSecondaryModelId(9L);
        service.createAgent(create);
        var partialUpdate = new AgentBo();
        partialUpdate.setName("Demo v2");

        assertThat(service.updateAgent("demo", partialUpdate).getSecondaryModelId()).isNull();
    }

    @Test
    void updateAgentKeepsCitationsToggleWhenUnspecified() {
        var create = bo("demo", "Demo", 7L);
        create.setShowCitations(false);
        service.createAgent(create);
        var update = new AgentBo();
        update.setName("Demo v2");

        assertThat(service.updateAgent("demo", update).getShowCitations()).isFalse();
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
        var strictService = new AgentServiceImpl(agentMapper, versionMapper, modelId -> false, deletedKeys::add,
                catalogProvider(modelCatalog));

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
                .contains("\"modelId\":7")
                .contains("\"secondaryModelId\":null")
                .contains("\"showCitations\":true")
                .contains("\"ragCallMode\":2");
        assertThat(agents.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(agents.get(0).getPublishedVersion()).isEqualTo(1L);
    }

    /**
     * 快照必须是**自洽的 JSON**：{@code secondaryModelId} 可空，用 {@code %d} 直接格式化会依赖
     * Formatter 对 null 的偶然行为，故单独断言能解析、且键值成对（见 AgentServiceImpl#jsonLong）。
     */
    @Test
    void publishSnapshotCarriesSecondaryModelAsParsableJson() {
        var bo = bo("demo", "Demo", 7L);
        bo.setSecondaryModelId(9L);
        bo.setShowCitations(false);
        service.createAgent(bo);

        service.publish("demo", "first release");

        var json = JSONUtil.parseObj(versions.get(0).getSnapshotJson());
        assertThat(json.getLong("modelId")).isEqualTo(7L);
        assertThat(json.getLong("secondaryModelId")).isEqualTo(9L);
        assertThat(json.getBool("showCitations")).isFalse();
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

    /**
     * mapper 打桩用的极简实体转 VO：只填本类断言用得到的字段。
     *
     * <p>刻意不调用生产的 {@code AgentServiceImpl#toVo}（私有）也不引 MapStruct——这里要验证的是
     * 「查询条件与返回记录」，不是字段映射；映射由 {@code createAgent*} / {@code updateAgent*}
     * 那批用例经 {@code getAgent} 覆盖。</p>
     */
    private static AgentVo toVo(Agent agent) {
        var vo = new AgentVo();
        vo.setId(agent.getId());
        vo.setAgentKey(agent.getAgentKey());
        vo.setName(agent.getName());
        vo.setStatus(agent.getStatus());
        vo.setModelId(agent.getModelId());
        vo.setSecondaryModelId(agent.getSecondaryModelId());
        vo.setShowCitations(!Boolean.FALSE.equals(agent.getShowCitations()));
        return vo;
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
        if (update.getSecondaryModelId() != null) {
            target.setSecondaryModelId(update.getSecondaryModelId());
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
        if (update.getRagCallMode() != null) {
            target.setRagCallMode(update.getRagCallMode());
        }
        if (update.getShowCitations() != null) {
            target.setShowCitations(update.getShowCitations());
        }
    }
}
