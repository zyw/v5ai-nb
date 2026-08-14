package xin.v5ai.nb.infrastructure.schema;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import xin.v5ai.nb.infrastructure.apiKey.ApiKeyEntity;
import xin.v5ai.nb.infrastructure.application.ApplicationEntity;
import xin.v5ai.nb.infrastructure.application.ApplicationVersionEntity;
import xin.v5ai.nb.infrastructure.auth.UserEntity;
import xin.v5ai.nb.infrastructure.model.ModelConfigEntity;
import xin.v5ai.nb.infrastructure.model.ModelProviderEntity;
import xin.v5ai.nb.infrastructure.rag.ApplicationKnowledgeBindingEntity;
import xin.v5ai.nb.infrastructure.rag.KnowledgeBaseEntity;
import xin.v5ai.nb.infrastructure.rag.KnowledgeDocumentEntity;
import xin.v5ai.nb.infrastructure.rag.KnowledgeTaskEntity;
import xin.v5ai.nb.infrastructure.runtime.AgentStateEntity;
import xin.v5ai.nb.infrastructure.runtime.RuntimeConversationEntity;
import xin.v5ai.nb.infrastructure.runtime.RuntimeMessageEntity;
import xin.v5ai.nb.infrastructure.runtime.RuntimeRunEntity;
import xin.v5ai.nb.infrastructure.runtime.RuntimeRunEventEntity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guards against entity/schema drift: every MyBatis-Plus entity column must exist in
 * some Flyway migration, otherwise writes fail at runtime (e.g. the missing
 * {@code provider_type} column that surfaced as INTERNAL_ERROR on provider creation).
 */
class SchemaConsistencyTest {

    @Test
    void everyEntityColumnExistsInMigrations() throws Exception {
        String migrations = readMigrations();
        for (Class<?> entityType : entityTypes()) {
            TableName tableName = entityType.getAnnotation(TableName.class);
            assertThat(tableName).as("entity %s must be @TableName", entityType.getSimpleName()).isNotNull();
            for (var field : entityType.getDeclaredFields()) {
                String column = columnFor(field);
                assertThat(migrations)
                        .as("column %s of %s must exist in a Flyway migration", column, entityType.getSimpleName())
                        .containsIgnoringCase(column);
            }
        }
    }

    private String columnFor(java.lang.reflect.Field field) {
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.value().isBlank()) {
            return tableField.value();
        }
        if (field.getAnnotation(TableId.class) != null && !field.getName().equals("id")) {
            return camelToSnake(field.getName());
        }
        return camelToSnake(field.getName());
    }

    private String camelToSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private List<Class<?>> entityTypes() {
        return List.of(
                UserEntity.class,
                ApiKeyEntity.class,
                ModelProviderEntity.class,
                ModelConfigEntity.class,
                ApplicationEntity.class,
                ApplicationVersionEntity.class,
                RuntimeConversationEntity.class,
                RuntimeMessageEntity.class,
                RuntimeRunEntity.class,
                RuntimeRunEventEntity.class,
                AgentStateEntity.class,
                KnowledgeBaseEntity.class,
                KnowledgeDocumentEntity.class,
                KnowledgeTaskEntity.class,
                ApplicationKnowledgeBindingEntity.class
        );
    }

    private String readMigrations() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath:db/migration/*.sql");
        assertThat(resources).as("Flyway migration files must exist").isNotEmpty();
        StringBuilder all = new StringBuilder();
        for (var resource : Arrays.stream(resources).sorted((a, b) -> a.getFilename().compareTo(b.getFilename())).toList()) {
            all.append(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).append('\n');
        }
        return all.toString();
    }
}
