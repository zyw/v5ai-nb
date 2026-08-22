package xin.v5ai.nb.infrastructure.schema;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import xin.v5ai.nb.infrastructure.apiKey.ApiKeyEntity;
import xin.v5ai.nb.infrastructure.agent.AgentEntity;
import xin.v5ai.nb.infrastructure.agent.AgentVersionEntity;
import xin.v5ai.nb.infrastructure.auth.UserEntity;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.infrastructure.rag.AgentKnowledgeBindingEntity;
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

    /**
     * V8 把 Application 相关表重命名为 Agent 表；重命名后的表没有自己的 CREATE TABLE
     * 块，校验时回退到原表名的 CREATE 块（列以最新迁移的 ALTER 为准）。
     */
    private static final java.util.Map<String, String> RENAMED_FROM = java.util.Map.of(
            "v5ai_agent", "v5ai_application",
            "v5ai_agent_version", "v5ai_application_version",
            "v5ai_agent_knowledge", "v5ai_application_knowledge",
            "v5ai_agent_mcp", "v5ai_application_mcp",
            "v5ai_agent_skill", "v5ai_application_skill");

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

    /**
     * The platform treats conversation/run ids as opaque strings end to end (API,
     * domain, entities), so runtime tables must use varchar ids. A uuid column would
     * make PostgreSQL reject {@code uuid = character varying} comparisons (chat 500s).
     * V1 historically declared uuid columns; V4 converts them. Guards:
     * 1) every uuid column in V1 must be converted by V4;
     * 2) the newest migration must not introduce new uuid columns.
     */
    @Test
    void runtimeSchemaMustNotUseUuidColumns() throws Exception {
        String v1 = read("V1__phase1_schema.sql");
        String v4 = read("V4__runtime_columns_to_varchar.sql");

        var uuidColumnPattern = java.util.regex.Pattern.compile("(?m)^\\s*(\\w+)\\s+UUID\\b");
        var matcher = uuidColumnPattern.matcher(v1);
        var uuidColumns = new java.util.ArrayList<String>();
        while (matcher.find()) {
            uuidColumns.add(matcher.group(1));
        }
        assertThat(uuidColumns).as("V1 must declare the uuid columns being converted").isNotEmpty();
        for (String column : uuidColumns) {
            assertThat(v4)
                    .as("V4 must convert uuid column %s to VARCHAR", column)
                    .contains("ALTER COLUMN " + column + " TYPE VARCHAR(36)");
        }

        String v4WithoutComments = v4.replaceAll("(?m)^--.*$", "");
        assertThat(v4WithoutComments)
                .as("newest migration must not introduce uuid columns")
                .doesNotContain("uuid")
                .doesNotContain("UUID");
    }

    private String read(String filename) throws Exception {
        var resource = new PathMatchingResourcePatternResolver()
                .getResource("classpath:db/migration/" + filename);
        assertThat(resource.exists()).as("migration %s must exist", filename).isTrue();
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String tableBlock(String migrations, String table) {
        // 重命名后的表回退到原表名的 CREATE 块（列由后续 ALTER 追加）
        String createTable = RENAMED_FROM.containsKey(table) ? RENAMED_FROM.get(table) : table;
        int start = migrations.indexOf("CREATE TABLE " + createTable + " (");
        String block = "";
        if (start >= 0) {
            int next = migrations.indexOf("CREATE TABLE", start + 1);
            block = next < 0 ? migrations.substring(start) : migrations.substring(start, next);
        }
        // 追加该表（或原表名）的全部 ALTER 段，覆盖列重命名/新增
        for (String candidate : new String[]{table, createTable}) {
            int searchFrom = 0;
            while (true) {
                int alter = migrations.indexOf("ALTER TABLE " + candidate, searchFrom);
                if (alter < 0) {
                    break;
                }
                int end = migrations.indexOf(';', alter);
                block += "\n" + (end < 0 ? migrations.substring(alter) : migrations.substring(alter, end));
                searchFrom = alter + 1;
            }
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
                V5aiModelProvider.class,
                V5aiModel.class,
                AgentEntity.class,
                AgentVersionEntity.class,
                RuntimeConversationEntity.class,
                RuntimeMessageEntity.class,
                RuntimeRunEntity.class,
                RuntimeRunEventEntity.class,
                AgentStateEntity.class,
                KnowledgeBaseEntity.class,
                KnowledgeDocumentEntity.class,
                KnowledgeTaskEntity.class,
                AgentKnowledgeBindingEntity.class,
                xin.v5ai.nb.skill.domain.Skill.class,
                xin.v5ai.nb.skill.domain.SkillVersion.class,
                xin.v5ai.nb.skill.domain.SkillFile.class,
                xin.v5ai.nb.skill.domain.AgentSkillBinding.class,
                xin.v5ai.nb.mcp.domain.McpServer.class,
                xin.v5ai.nb.mcp.domain.McpTool.class,
                xin.v5ai.nb.mcp.domain.McpToolCallAudit.class,
                xin.v5ai.nb.mcp.domain.AgentMcpBinding.class
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
