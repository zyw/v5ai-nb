package xin.v5ai.nb.rag.core.store;

import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TextArrayTypeHandler} 单测：两种业务库方言下的绑定与读取
 * （PG {@code text[]} 含 NULL 数组/NULL 元素；MySQL {@code JSON} 数组含空数组）。
 *
 * @author ZYW
 * @since 2026-09-09
 */
class TextArrayTypeHandlerTest {

    private final TextArrayTypeHandler handler = new TextArrayTypeHandler();

    /**
     * 造一个按产品名应答的连接（处理器就靠这个判方言）。
     */
    private static Connection connectionOf(String product) throws SQLException {
        var connection = mock(Connection.class);
        var metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(product);
        return connection;
    }

    @Test
    void bindsStringArrayAsPgTextArray() throws SQLException {
        var ps = mock(PreparedStatement.class);
        var connection = connectionOf("PostgreSQL");
        var array = mock(Array.class);
        when(ps.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"), aryEq(new String[]{"温度", "控制"}))).thenReturn(array);

        handler.setNonNullParameter(ps, 1, new String[]{"温度", "控制"}, null);

        verify(connection).createArrayOf(eq("text"), aryEq(new String[]{"温度", "控制"}));
        verify(ps).setArray(1, array);
    }

    @Test
    void bindsStringArrayAsJsonArrayOnMysql() throws SQLException {
        var ps = mock(PreparedStatement.class);
        // 先建好 connection 再打桩：mock 的创建不能嵌在 when(...) 里，否则 Mockito 视嵌套打桩为未完成
        var connection = connectionOf("MySQL");
        when(ps.getConnection()).thenReturn(connection);

        handler.setNonNullParameter(ps, 1, new String[]{"温度", "控制"}, null);

        verify(ps).setString(1, "[\"温度\",\"控制\"]");
    }

    @Test
    void readsPgTextArrayAsStringArray() throws SQLException {
        var rs = mock(ResultSet.class);
        var array = mock(Array.class);
        when(rs.getObject("keyword_tokens")).thenReturn(array);
        when(array.getArray()).thenReturn(new Object[]{"温度", null, "控制"});

        assertThat(handler.getNullableResult(rs, "keyword_tokens")).containsExactly("温度", null, "控制");
    }

    @Test
    void readsMysqlJsonArrayAsStringArray() throws SQLException {
        var rs = mock(ResultSet.class);
        // Connector/J 对 JSON 列的 getObject 返回 JSON 文本串
        when(rs.getObject("keyword_tokens")).thenReturn("[\"温度\",\"控制\"]");

        assertThat(handler.getNullableResult(rs, "keyword_tokens")).containsExactly("温度", "控制");
    }

    @Test
    void emptyJsonArrayReadsAsEmptyNotNull() throws SQLException {
        var rs = mock(ResultSet.class);
        when(rs.getObject(3)).thenReturn("[]");

        assertThat(handler.getNullableResult(rs, 3)).isEmpty();
    }

    @Test
    void nullColumnYieldsNull() throws SQLException {
        var rs = mock(ResultSet.class);

        assertThat(handler.getNullableResult(rs, "keyword_tokens")).isNull();
        assertThat(handler.getNullableResult(rs, 3)).isNull();
    }
}
