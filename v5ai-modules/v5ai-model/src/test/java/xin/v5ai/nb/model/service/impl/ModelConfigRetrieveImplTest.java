package xin.v5ai.nb.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelConfigRetrieveImplTest {

    @Test
    void selectsEnabledDefaultBeforeEnabledFallbackForRequestedType() {
        var modelMapper = mock(V5aiModelMapper.class);
        var providerMapper = mock(V5aiModelProviderMapper.class);
        var selected = model(7L, "CHAT", true, true);
        when(modelMapper.selectOne(any())).thenReturn(selected);
        when(providerMapper.selectById(3L)).thenReturn(provider());

        var result = new ModelConfigRetrieveImpl(modelMapper, providerMapper)
                .findDefaultOrFirstEnabledModel("CHAT");

        assertThat(result.modelId()).isEqualTo(7L);
        var query = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        // The selector must constrain by type, enabled state, default-first ordering, and ID tie-break.
        org.mockito.Mockito.verify(modelMapper).selectOne(query.capture());
        assertThat(query.getValue()).isNotNull();
    }

    @Test
    void reportsMissingModelConfigurationInsteadOfReturningNull() {
        var modelMapper = mock(V5aiModelMapper.class);
        var providerMapper = mock(V5aiModelProviderMapper.class);
        when(modelMapper.selectOne(any())).thenReturn(null);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new ModelConfigRetrieveImpl(modelMapper, providerMapper)
                        .findDefaultOrFirstEnabledModel("EMBEDDING")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMBEDDING");
    }

    private static V5aiModel model(Long id, String type, boolean enabled, boolean isDefault) {
        var model = new V5aiModel();
        model.setId(id);
        model.setProviderId(3L);
        model.setModelKey("llama3");
        model.setModelType(type);
        model.setEnabled(enabled);
        model.setIsDefault(isDefault);
        return model;
    }

    private static V5aiModelProvider provider() {
        var provider = new V5aiModelProvider();
        provider.setId(3L);
        provider.setProviderKey("ollama");
        return provider;
    }
}
