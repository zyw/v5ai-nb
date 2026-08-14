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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against entity/schema drift: every MyBatis-Plus entity column must exist in
 * the CREATE TABLE block of its own table in the Flyway migrations. Column names are
 * derived the way MyBatis-Plus derives them (a field with {@code @TableId} uses the
 * {@code @TableId} value or the camelCase-to-snake_case field name — any
 * {@code @TableField} on the same field is ignored), so the test also catches the
 * {@code @TableId}+{@code @TableField} conflict (e.g. conversationId mapped to the
 * non-existent {@code conversation_id} column of {@code v5ai_conversation}).
 */
class SchemaConsistencyTest {

    @Test
    void everyEntityColumnExistsInItsOwnTableBlock() throws Exception {
        String migrations = readMigrations();
        for (Class<?> entityType : entityTypes()) {
            TableName tableName = entityType.getAnnotation(TableName.class);
            assertThat(tableName).as("entity %s must be @TableName", entityType.getSimpleName()).isNotNull();
            String block = tableBlock(migrations, tableName.value());
            assertThat(block).as("CREATE TABLE %s must exist in migrations", tableName.value())
                    .isNotBlank();
            for (Field field : entityType.getDeclaredFields()) {
                String column = columnFor(field);
                assertThat(block)
                        .as("column %s of table %s (entity %s) must exist",
                                column, tableName.value(), entityType.getSimpleName())
                        .containsIgnoringCase(column);
            }
        }
    }

    private String tableBlock(String migrations, String table) {
        int start = migrations.indexOf("CREATE TABLE " + table + " (");
        String block = "";
        if (start >= 0) {
            int next = migrations.indexOf("CREATE TABLE", start + 1);
            block = next < 0 ? migrations.substring(start) : migrations.substring(start, next);
        }
        int searchFrom = 0;
        while (true) {
            int alter = migrations.indexOf("ALTER TABLE " + table, searchFrom);
            if (alter < 0) {
                break;
            }
            int end = migrations.indexOf(';', alter);
            block += "\n" + (end < 0 ? migrations.substring(alter) : migrations.substring(alter, end));
            searchFrom = alter + 1;
        }
        return block;
    }

    private String columnFor(Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        if (tableId != null) {
            return tableId.value().isBlank() ? camelToSnake(field.getName()) : tableId.value();
        }
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.value().isBlank()) {
            return tableField.value();
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
