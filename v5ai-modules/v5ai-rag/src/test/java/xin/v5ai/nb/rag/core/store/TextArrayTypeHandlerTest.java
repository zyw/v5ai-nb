package xin.v5ai.nb.rag.core.store;

import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
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
 * {@link TextArrayTypeHandler} 单测：PG {@code text[]} 的绑定与读取（含 NULL 数组、NULL 元素）。
 *
 * @author ZYW
 * @since 2026-09-09
 */
class TextArrayTypeHandlerTest {

    private final TextArrayTypeHandler handler = new TextArrayTypeHandler();

    @Test
    void bindsStringArrayAsPgTextArray() throws SQLException {
        var ps = mock(PreparedStatement.class);
        var connection = mock(Connection.class);
        var array = mock(Array.class);
        when(ps.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"), aryEq(new String[]{"温度", "控制"}))).thenReturn(array);

        handler.setNonNullParameter(ps, 1, new String[]{"温度", "控制"}, null);

        verify(connection).createArrayOf(eq("text"), aryEq(new String[]{"温度", "控制"}));
        verify(ps).setArray(1, array);
    }

    @Test
    void readsPgTextArrayAsStringArray() throws SQLException {
        var rs = mock(ResultSet.class);
        var array = mock(Array.class);
        when(rs.getArray("keyword_tokens")).thenReturn(array);
        when(array.getArray()).thenReturn(new Object[]{"温度", null, "控制"});

        assertThat(handler.getNullableResult(rs, "keyword_tokens")).containsExactly("温度", null, "控制");
    }

    @Test
    void nullColumnYieldsNull() throws SQLException {
        var rs = mock(ResultSet.class);

        assertThat(handler.getNullableResult(rs, "keyword_tokens")).isNull();
        assertThat(handler.getNullableResult(rs, 3)).isNull();
    }
}
