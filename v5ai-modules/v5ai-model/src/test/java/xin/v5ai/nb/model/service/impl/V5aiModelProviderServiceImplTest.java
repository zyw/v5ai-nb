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
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelProviderBo;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link V5aiModelProviderServiceImpl} 的 Mockito 单测：重点覆盖「存在模型引用的供应商不可停用」守卫。
 *
 * @author ZYW
 * @since 2026-09-04
 */
class V5aiModelProviderServiceImplTest {

    private V5aiModelProviderMapper mapper;
    private V5aiModelMapper modelMapper;
    private V5aiModelProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境没有 MyBatis 启动流程，手动初始化实体 TableInfo，
        // 使服务内 LambdaQueryWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, V5aiModel.class);

        // MapstructUtils 在类加载时通过 SpringUtil 取 Converter Bean，
        // 注册一个按属性名拷贝的 Converter 使 BO→实体转换可独立测试。
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", new CopyConverter());
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        mapper = mock(V5aiModelProviderMapper.class);
        modelMapper = mock(V5aiModelMapper.class);
        service = new V5aiModelProviderServiceImpl(mapper, modelMapper);
    }

    @Test
    void rejectsDisablingProviderReferencedByModels() {
        when(mapper.selectById(1L)).thenReturn(existing(true));
        when(modelMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.updateByBo(bo(false)))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("无法停用");

        verify(mapper, never()).updateById(any(V5aiModelProvider.class));
    }

    @Test
    void allowsDisablingProviderWithoutModels() {
        when(mapper.selectById(1L)).thenReturn(existing(true));
        when(modelMapper.selectCount(any())).thenReturn(0L);
        when(mapper.updateById(any(V5aiModelProvider.class))).thenReturn(1);

        assertThat(service.updateByBo(bo(false))).isTrue();
    }

    @Test
    void skipsModelCheckWhenStayingEnabled() {
        when(mapper.selectById(1L)).thenReturn(existing(true));
        when(mapper.updateById(any(V5aiModelProvider.class))).thenReturn(1);

        assertThat(service.updateByBo(bo(true))).isTrue();
        verify(modelMapper, never()).selectCount(any());
    }

    @Test
    void allowsEnablingDisabledProvider() {
        when(mapper.selectById(1L)).thenReturn(existing(false));
        when(mapper.updateById(any(V5aiModelProvider.class))).thenReturn(1);

        assertThat(service.updateByBo(bo(true))).isTrue();
        verify(modelMapper, never()).selectCount(any());
    }

    @Test
    void allowsReDisablingAlreadyDisabledProvider() {
        when(mapper.selectById(1L)).thenReturn(existing(false));
        when(mapper.updateById(any(V5aiModelProvider.class))).thenReturn(1);

        assertThat(service.updateByBo(bo(false))).isTrue();
        verify(modelMapper, never()).selectCount(any());
    }

    private V5aiModelProvider existing(boolean enabled) {
        V5aiModelProvider p = new V5aiModelProvider();
        p.setId(1L);
        p.setProviderKey("openai");
        p.setName("OpenAI");
        p.setEnabled(enabled);
        return p;
    }

    private ModelProviderBo bo(boolean enabled) {
        ModelProviderBo b = new ModelProviderBo();
        b.setId(1L);
        b.setName("OpenAI");
        b.setEnabled(enabled);
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
