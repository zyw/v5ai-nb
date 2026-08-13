package xin.v5ai.nb.infrastructure.model;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.model.ModelProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisModelProviderRepositoryTest {
    @Test
    void savesProviderEntityWithV5aiFields() {
        var mapper = new RecordingModelProviderMapper();
        var repository = new MyBatisModelProviderRepository(mapper);

        repository.save(new ModelProvider("openai", "OpenAI", "OPENAI", true));

        assertThat(mapper.saved.getProviderKey()).isEqualTo("openai");
        assertThat(mapper.saved.getName()).isEqualTo("OpenAI");
        assertThat(mapper.saved.getProviderType()).isEqualTo("OPENAI");
        assertThat(mapper.saved.getEnabled()).isTrue();
    }

    @Test
    void findsProviderByKey() {
        var mapper = new RecordingModelProviderMapper();
        mapper.entities.add(entity("openai", "OpenAI", "OPENAI", true));
        var repository = new MyBatisModelProviderRepository(mapper);

        ModelProvider provider = repository.findByKey("openai");

        assertThat(provider.providerKey()).isEqualTo("openai");
        assertThat(provider.enabled()).isTrue();
    }

    @Test
    void listsProviders() {
        var mapper = new RecordingModelProviderMapper();
        mapper.entities.add(entity("openai", "OpenAI", "OPENAI", true));
        mapper.entities.add(entity("dashscope", "DashScope", "DASHSCOPE", false));
        var repository = new MyBatisModelProviderRepository(mapper);

        List<ModelProvider> providers = repository.list();

        assertThat(providers).extracting(ModelProvider::providerKey)
                .containsExactly("openai", "dashscope");
    }

    private static ModelProviderEntity entity(String key, String name, String type, boolean enabled) {
        var entity = new ModelProviderEntity();
        entity.setProviderKey(key);
        entity.setName(name);
        entity.setProviderType(type);
        entity.setEnabled(enabled);
        return entity;
    }

    private static final class RecordingModelProviderMapper implements ModelProviderGateway {
        private ModelProviderEntity saved;
        private final List<ModelProviderEntity> entities = new ArrayList<>();

        @Override
        public void insertProvider(ModelProviderEntity entity) {
            this.saved = entity;
            entities.add(entity);
        }

        @Override
        public ModelProviderEntity selectByProviderKey(String providerKey) {
            return entities.stream()
                    .filter(entity -> entity.getProviderKey().equals(providerKey))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ModelProviderEntity> selectProviders() {
            return entities;
        }
    }
}
