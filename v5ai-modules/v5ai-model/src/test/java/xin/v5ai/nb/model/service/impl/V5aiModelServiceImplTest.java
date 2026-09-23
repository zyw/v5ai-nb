package xin.v5ai.nb.model.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.model.core.ModelConnectionTester;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link V5aiModelServiceImpl} 的 Mockito 单测：
 * 覆盖「禁用前引用校验」「停用默认模型自动清除默认」「同类型默认唯一」「删除引用校验」等守卫。
 *
 * @author ZYW
 * @since 2026-09-05
 */
class V5aiModelServiceImplTest {

    private V5aiModelMapper mapper;
    private V5aiModelProviderMapper providerMapper;
    private CredentialCipher credentialCipher;
    private V5aiModelServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境没有 MyBatis 启动流程，手动初始化实体 TableInfo，
        // 使服务内 LambdaQueryWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, V5aiModel.class);
        TableInfoHelper.initTableInfo(assistant, V5aiModelProvider.class);

        // MapstructUtils 在类加载时通过 SpringUtil 取 Converter Bean，
        // 注册一个按属性名拷贝的 Converter 使 BO→实体转换可独立测试。
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", new CopyConverter());
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        mapper = mock(V5aiModelMapper.class);
        providerMapper = mock(V5aiModelProviderMapper.class);
        credentialCipher = mock(CredentialCipher.class);
        service = new V5aiModelServiceImpl(mapper, providerMapper, credentialCipher, mock(ModelConnectionTester.class));
    }

    // ---------- updateEnabled：启用/停用 ----------

    @Test
    void rejectsDisablingModelReferencedByAgents() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.countAgentUsage(1L)).thenReturn(2L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.updateEnabled(1L, false))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("2 个 AgentDTO")
                .hasMessageContaining("无法禁用");

        verify(mapper, never()).updateById(any(V5aiModel.class));
    }

    @Test
    void rejectsDisablingModelReferencedByKnowledgeBases() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.countAgentUsage(1L)).thenReturn(0L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.updateEnabled(1L, false))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("3 个知识库");
    }

    @Test
    void allowsDisablingUnreferencedModel() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.countAgentUsage(1L)).thenReturn(0L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(0L);
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        assertThat(service.updateEnabled(1L, false)).isTrue();
        verify(mapper).updateById(any(V5aiModel.class));
        verify(mapper, never()).clearModelTypeDefaults(any(), any());
    }

    @Test
    void disablingDefaultModelAlsoClearsItsDefaultFlag() {
        when(mapper.selectById(1L)).thenReturn(existing(true, true));
        when(mapper.countAgentUsage(1L)).thenReturn(0L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(0L);
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        assertThat(service.updateEnabled(1L, false)).isTrue();
        verify(mapper).updateById(org.mockito.ArgumentMatchers.<V5aiModel>argThat(u ->
                !u.getEnabled() && !u.getIsDefault()));
    }

    @Test
    void allowsEnablingDisabledModel() {
        when(mapper.selectById(1L)).thenReturn(existing(false, false));
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        assertThat(service.updateEnabled(1L, true)).isTrue();
        verify(mapper).updateById(org.mockito.ArgumentMatchers.<V5aiModel>argThat(u -> u.getEnabled()));
    }

    @Test
    void isIdempotentWhenStateUnchanged() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));

        assertThat(service.updateEnabled(1L, true)).isTrue();
        verify(mapper, never()).updateById(any(V5aiModel.class));
        verify(mapper, never()).countAgentUsage(1L);
    }

    @Test
    void rejectsNullEnabled() {
        assertThatThrownBy(() -> service.updateEnabled(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- updateDefault：默认唯一 ----------

    @Test
    void rejectsSettingDefaultOnDisabledModel() {
        when(mapper.selectById(1L)).thenReturn(existing(false, false));

        assertThatThrownBy(() -> service.updateDefault(1L, true))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("不能设为默认");

        verify(mapper, never()).updateById(any(V5aiModel.class));
    }

    @Test
    void settingDefaultClearsOtherDefaultsOfSameType() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        assertThat(service.updateDefault(1L, true)).isTrue();
        verify(mapper).clearModelTypeDefaults("CHAT", 1L);
        verify(mapper).updateById(org.mockito.ArgumentMatchers.<V5aiModel>argThat(u -> u.getIsDefault()));
    }

    @Test
    void cancelingDefaultDoesNotClearOthers() {
        when(mapper.selectById(1L)).thenReturn(existing(true, true));
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        assertThat(service.updateDefault(1L, false)).isTrue();
        verify(mapper, never()).clearModelTypeDefaults(any(), any());
        verify(mapper).updateById(org.mockito.ArgumentMatchers.<V5aiModel>argThat(u -> !u.getIsDefault()));
    }

    // ---------- deleteWithValidById ----------

    @Test
    void rejectsDeletingModelUsedByKnowledgeBase() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.countAgentUsage(1L)).thenReturn(0L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteWithValidById(1L, true))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("无法删除");

        verify(mapper, never()).deleteById(1L);
    }

    @Test
    void allowsDeletingUnreferencedModel() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.countAgentUsage(1L)).thenReturn(0L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(0L);
        when(mapper.deleteById(1L)).thenReturn(1);

        assertThat(service.deleteWithValidById(1L, true)).isTrue();
    }

    // ---------- updateByBo（编辑接口旁路守卫） ----------

    @Test
    void updateByBoCannotDisableReferencedModel() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.countAgentUsage(1L)).thenReturn(1L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(0L);

        ModelBo bo = editBo();
        bo.setEnabled(false);

        assertThatThrownBy(() -> service.updateByBo(bo))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("无法禁用");
        verify(mapper, never()).updateById(any(V5aiModel.class));
    }

    @Test
    void updateByBoClearsDefaultWhenDisablingDefaultModel() {
        when(mapper.selectById(1L)).thenReturn(existing(true, true));
        when(mapper.countAgentUsage(1L)).thenReturn(0L);
        when(mapper.countKnowledgeBaseUsage(1L)).thenReturn(0L);
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        ModelBo bo = editBo();
        bo.setEnabled(false);

        assertThat(service.updateByBo(bo)).isTrue();
        verify(mapper, never()).clearModelTypeDefaults(any(), any());
        verify(mapper).updateById(org.mockito.ArgumentMatchers.<V5aiModel>argThat(u ->
                !u.getEnabled() && !u.getIsDefault()));
    }

    @Test
    void updateByBoSettingDefaultClearsSameTypeOthers() {
        when(mapper.selectById(1L)).thenReturn(existing(true, false));
        when(mapper.updateById(any(V5aiModel.class))).thenReturn(1);

        ModelBo bo = editBo();
        bo.setIsDefault(true);

        assertThat(service.updateByBo(bo)).isTrue();
        verify(mapper).clearModelTypeDefaults("CHAT", 1L);
    }

    // ---------- insertByBo ----------

    @Test
    void creatingDefaultModelClearsSameTypeOthers() {
        V5aiModelProvider provider = new V5aiModelProvider();
        provider.setId(3L);
        provider.setEnabled(true);
        when(providerMapper.selectById(3L)).thenReturn(provider);
        when(credentialCipher.encrypt(any())).thenReturn("ciphertext");
        doAnswer(inv -> {
            inv.getArgument(0, V5aiModel.class).setId(42L);
            return 1;
        }).when(mapper).insert(any(V5aiModel.class));

        ModelBo bo = editBo();
        bo.setProviderId(3L);
        bo.setIsDefault(true);

        assertThat(service.insertByBo(bo)).isTrue();
        verify(mapper).clearModelTypeDefaults(any(), eq(42L));
    }

    @Test
    void modelOptionsUseIdBasedLabels() {
        var model = new V5aiModelVo();
        model.setId(42L);
        model.setProviderId(3L);
        model.setModelKey("llama3");
        model.setModelName("Ollama A");
        model.setModelType("CHAT");
        model.setIsDefault(false);
        when(mapper.selectVoList(any())).thenReturn(java.util.List.of(model));

        List<OptionDTO> options = service.queryOptionList(new ModelBo());

        assertThat(options).singleElement().extracting(OptionDTO::label)
                .isEqualTo("42/Ollama A (CHAT)");
    }

    // ---------- helpers ----------

    private V5aiModel existing(boolean enabled, boolean isDefault) {
        V5aiModel m = new V5aiModel();
        m.setId(1L);
        m.setProviderId(3L);
        m.setModelKey("deepseek-v4-flash");
        m.setModelType("CHAT");
        m.setEnabled(enabled);
        m.setIsDefault(isDefault);
        return m;
    }

    private ModelBo editBo() {
        ModelBo b = new ModelBo();
        b.setId(1L);
        b.setModelKey("deepseek-v4-flash");
        return b;
    }

    /**
     * 按属性名拷贝的 Converter：单测中替代 mapstruct-plus 的 Spring 装配。
     */
    private static class CopyConverter extends Converter {
        @Override
        public <S, T> T convert(S source, Class<T> desc) {
            if (source == null) {
                return null;
            }
            try {
                T target = desc.getDeclaredConstructor().newInstance();
                BeanUtil.copyProperties(source, target);
                return target;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
