package xin.v5ai.nb.platform.apiKey;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.platform.domain.ApiKey;
import xin.v5ai.nb.platform.mapper.ApiKeyMapper;
import xin.v5ai.nb.platform.service.impl.MyBatisAgentApiKeyStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisAgentApiKeyStoreTest {

    /** 内存版 ApiKeyMapper：按原 upsert（存在则更新，否则插入）语义模拟。 */
    private static ApiKeyMapper inMemoryMapper() {
        Map<String, ApiKey> entities = new ConcurrentHashMap<>();
        var mapper = mock(ApiKeyMapper.class);
        when(mapper.selectByAgentKey(anyString())).thenAnswer(inv -> entities.get(inv.<String>getArgument(0)));
        when(mapper.selectAll()).thenAnswer(inv -> new ArrayList<>(entities.values()));
        doAnswer(inv -> {
            var entity = inv.<ApiKey>getArgument(0);
            var existing = entities.get(entity.getAgentKey());
            if (existing == null) {
                entities.put(entity.getAgentKey(), entity);
            } else {
                entity.setId(existing.getId());
                entities.put(entity.getAgentKey(), entity);
            }
            return null;
        }).when(mapper).upsert(any(ApiKey.class));
        doAnswer(inv -> {
            entities.remove(inv.<String>getArgument(0));
            return null;
        }).when(mapper).deleteByAgentKey(anyString());
        return mapper;
    }

    @Test
    void upsertStoresHashAndFindReturnsIt() {
        var store = new MyBatisAgentApiKeyStore(inMemoryMapper());

        store.upsertSecret("demo", "hash-1");

        assertThat(store.findSecretHash("demo")).isEqualTo("hash-1");
    }

    @Test
    void upsertReplacesExistingKeyForApp() {
        var store = new MyBatisAgentApiKeyStore(inMemoryMapper());
        store.upsertSecret("demo", "hash-1");

        store.upsertSecret("demo", "hash-2");

        assertThat(store.findSecretHash("demo")).isEqualTo("hash-2");
    }

    @Test
    void disabledKeyIsNotReturnedToVerifier() {
        var mapper = inMemoryMapper();
        var store = new MyBatisAgentApiKeyStore(mapper);
        store.upsertSecret("demo", "hash-1");
        mapper.selectByAgentKey("demo").setEnabled(false);

        assertThat(store.findSecretHash("demo")).isNull();
    }

    @Test
    void listAndDeleteWork() {
        var store = new MyBatisAgentApiKeyStore(inMemoryMapper());
        store.upsertSecret("demo", "hash-1");

        assertThat(store.list()).extracting(AgentApiKeyStore.ApiKeyRecord::agentKey).containsExactly("demo");

        store.delete("demo");

        assertThat(store.findSecretHash("demo")).isNull();
    }
}
