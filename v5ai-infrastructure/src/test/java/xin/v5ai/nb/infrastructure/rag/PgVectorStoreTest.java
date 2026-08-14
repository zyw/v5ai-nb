package xin.v5ai.nb.infrastructure.rag;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import xin.v5ai.nb.rag.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorStoreTest {
    @Test
    void storeUsesPgvectorLiteralAndJsonbMetadata() {
        var recorder = new RecordingJdbcTemplate();
        var store = new PgVectorStore(recorder);

        store.store(List.of(new VectorStore.VectorChunk(1L, 10L, 0, "content", List.of(1.0f, 2.0f),
                "{\"documentId\":10}")));

        assertThat(recorder.lastSql).contains("?::vector").contains("?::jsonb");
        assertThat(recorder.lastParams).contains("[1.0,2.0]");
        assertThat(recorder.lastParams).contains("{\"documentId\":10}");
    }

    @Test
    void searchBuildsCosineQueryWithKnowledgeBaseInClause() {
        var recorder = new RecordingJdbcTemplate();
        var store = new PgVectorStore(recorder);

        store.search(List.of(1L, 2L), List.of(0.5f, 0.5f), 3);

        assertThat(recorder.lastSql).contains("embedding <=> ?::vector");
        assertThat(recorder.lastSql).contains("knowledge_base_id IN (?,?)");
        assertThat(recorder.lastSql).contains("LIMIT ?");
    }

    @Test
    void keywordSearchUsesIlike() {
        var recorder = new RecordingJdbcTemplate();
        var store = new PgVectorStore(recorder);

        store.keywordSearch(List.of(1L), "v5ai", 2);

        assertThat(recorder.lastSql).contains("ILIKE ?");
        assertThat(recorder.lastParams).contains("%v5ai%");
    }

    @Test
    void searchWithNoKnowledgeBasesReturnsEmpty() {
        var recorder = new RecordingJdbcTemplate();
        var store = new PgVectorStore(recorder);

        assertThat(store.search(List.of(), List.of(1.0f), 5)).isEmpty();
        assertThat(recorder.lastSql).isNull();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String lastSql;
        private Object[] lastParams;

        @Override
        public int update(String sql, Object... args) {
            lastSql = sql;
            lastParams = args;
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastSql = sql;
            lastParams = args;
            return List.of();
        }
    }
}
