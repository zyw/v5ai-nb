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
        assertThat(mapper.entity.getEnabled()).isTrue();
    }

    @Test
    void listUsesJoinedProviderKey() {
        var mapper = new RecordingModelConfigMapper();
        var row = new ModelConfigRow();
        row.setId(1L);
        row.setProviderKey("openai");
        row.setModelKey("gpt-4o");
        row.setModelType("CHAT");
        row.setCredentialsCiphertext("cipher");
        row.setEnabled(true);
        mapper.rows.add(row);
        var repository = new MyBatisModelRepository(mapper, providerKey -> 42L);

        List<ModelConfig> models = repository.list();

        assertThat(models).hasSize(1);
        assertThat(models.getFirst().id()).isEqualTo(1L);
        assertThat(models.getFirst().providerKey()).isEqualTo("openai");
        assertThat(models.getFirst().enabled()).isTrue();
    }

    @Test
    void updateModelWritesEntityWithIdAndProviderId() {
        var mapper = new RecordingModelConfigMapper();
        var repository = new MyBatisModelRepository(mapper, providerKey -> 7L);

        repository.update(new ModelConfig(9L, "openai", "gpt-4o-mini", ModelType.CHAT, "cipher-new", true));

        assertThat(mapper.updated.getId()).isEqualTo(9L);
        assertThat(mapper.updated.getProviderId()).isEqualTo(7L);
        assertThat(mapper.updated.getModelKey()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void deleteModelDelegatesToGateway() {
        var mapper = new RecordingModelConfigMapper();
        var repository = new MyBatisModelRepository(mapper, providerKey -> 42L);

        repository.delete(5L);

        assertThat(mapper.deletedId).isEqualTo(5L);
    }

    @Test
    void listOptionsUsesProjectionRow() {
        var mapper = new RecordingModelConfigMapper();
        var row = new ModelConfigRow();
        row.setId(1L);
        row.setProviderKey("openai");
        row.setModelKey("gpt-4o");
        row.setModelType("CHAT");
        mapper.rows.add(row);
        var repository = new MyBatisModelRepository(mapper, providerKey -> 42L);

        var options = repository.listOptions();

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().id()).isEqualTo(1L);
        assertThat(options.getFirst().providerKey()).isEqualTo("openai");
        assertThat(options.getFirst().modelKey()).isEqualTo("gpt-4o");
        assertThat(options.getFirst().type()).isEqualTo(ModelType.CHAT);
    }

    private static final class RecordingModelConfigMapper implements ModelConfigGateway {
        private ModelConfigEntity entity;
        private ModelConfigEntity updated;
        private Long deletedId;
        private final List<ModelConfigRow> rows = new java.util.ArrayList<>();

        @Override
        public void insertModel(ModelConfigEntity entity) {
            this.entity = entity;
        }

        @Override
        public void updateModel(ModelConfigEntity entity) {
            this.updated = entity;
        }

        @Override
        public void deleteModel(Long modelId) {
            this.deletedId = modelId;
        }

        @Override
        public ModelConfigEntity selectModelById(Long modelId) {
            return null;
        }

        @Override
        public List<ModelConfigEntity> selectModels() {
            return List.of();
        }

        @Override
        public List<ModelConfigRow> selectModelRows() {
            return rows;
        }

        @Override
        public List<ModelConfigRow> selectModelOptions() {
            return rows;
        }
    }
}
