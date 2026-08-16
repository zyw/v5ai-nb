package xin.v5ai.nb.infrastructure.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TablePrefixTest {
    @Test
    void phaseOneTablesUseV5aiPrefix() throws IOException {
        var resource = getClass().getResourceAsStream("/db/migration/V1__phase1_schema.sql");
        var migrationSql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(migrationSql).contains("CREATE TABLE v5ai_model_provider");
        assertThat(migrationSql).contains("CREATE TABLE v5ai_application");
        assertThat(migrationSql).contains("CREATE TABLE v5ai_conversation");
        assertThat(migrationSql).contains("CREATE TABLE v5ai_message");
    }

    @Test
    void agentRenameMigrationUsesV5aiPrefix() throws IOException {
        var resource = getClass().getResourceAsStream("/db/migration/V8__agent_rename.sql");
        var migrationSql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(migrationSql).contains("ALTER TABLE v5ai_application RENAME TO v5ai_agent");
        assertThat(migrationSql).contains("ALTER TABLE v5ai_application_version RENAME TO v5ai_agent_version");
        assertThat(migrationSql).contains("ALTER TABLE v5ai_api_key RENAME COLUMN app_key TO agent_key");
    }
}
