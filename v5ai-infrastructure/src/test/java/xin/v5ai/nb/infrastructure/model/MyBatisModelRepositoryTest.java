package xin.v5ai.nb.infrastructure.model;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.model.ModelConfig;
import xin.v5ai.nb.model.ModelType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisModelRepositoryTest {
    @Test
    void savingModelResolvesProviderIdFromProviderKey() {
        var mapper = new RecordingModelConfigMapper();
        var repository = new MyBatisModelRepository(mapper, providerKey -> 42L);

        repository.save(new ModelConfig("openai", "gpt-4o", ModelType.CHAT, "ciphertext"));

        assertThat(mapper.entity.getProviderId()).isEqualTo(42L);
    }

    private static final class RecordingModelConfigMapper implements ModelConfigGateway {
        private ModelConfigEntity entity;

        @Override
        public void insertModel(ModelConfigEntity entity) {
            this.entity = entity;
        }

        @Override
        public List<ModelConfigEntity> selectModels() {
            return List.of();
        }
    }
}
