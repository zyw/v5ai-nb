package xin.v5ai.nb.infrastructure.apiKey;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisApplicationApiKeyStoreTest {
    @Test
    void upsertStoresHashAndFindReturnsIt() {
        var gateway = new RecordingApiKeyGateway();
        var store = new MyBatisApplicationApiKeyStore(gateway);

        store.upsertSecret("demo", "hash-1");

        assertThat(store.findSecretHash("demo")).isEqualTo("hash-1");
    }

    @Test
    void upsertReplacesExistingKeyForApp() {
        var gateway = new RecordingApiKeyGateway();
        var store = new MyBatisApplicationApiKeyStore(gateway);
        store.upsertSecret("demo", "hash-1");

        store.upsertSecret("demo", "hash-2");

        assertThat(store.findSecretHash("demo")).isEqualTo("hash-2");
        assertThat(gateway.entities).hasSize(1);
    }

    @Test
    void disabledKeyIsNotReturnedToVerifier() {
        var gateway = new RecordingApiKeyGateway();
        var store = new MyBatisApplicationApiKeyStore(gateway);
        store.upsertSecret("demo", "hash-1");
        gateway.entities.getFirst().setEnabled(false);

        assertThat(store.findSecretHash("demo")).isNull();
    }

    @Test
    void listAndDeleteWork() {
        var gateway = new RecordingApiKeyGateway();
        var store = new MyBatisApplicationApiKeyStore(gateway);
        store.upsertSecret("demo", "hash-1");

        assertThat(store.list()).extracting(ApplicationApiKeyStore.ApiKeyRecord::appKey).containsExactly("demo");

        store.delete("demo");

        assertThat(store.findSecretHash("demo")).isNull();
    }

    private static final class RecordingApiKeyGateway implements ApiKeyGateway {
        private final List<ApiKeyEntity> entities = new ArrayList<>();

        @Override
        public void upsert(ApiKeyEntity entity) {
            var existing = selectByAppKey(entity.getAppKey());
            if (existing == null) {
                entities.add(entity);
            } else {
                var index = entities.indexOf(existing);
                entity.setId(existing.getId());
                entities.set(index, entity);
            }
        }

        @Override
        public ApiKeyEntity selectByAppKey(String appKey) {
            return entities.stream().filter(entity -> entity.getAppKey().equals(appKey)).findFirst().orElse(null);
        }

        @Override
        public List<ApiKeyEntity> selectAll() {
            return entities;
        }

        @Override
        public void deleteByAppKey(String appKey) {
            entities.removeIf(entity -> entity.getAppKey().equals(appKey));
        }
    }
}
