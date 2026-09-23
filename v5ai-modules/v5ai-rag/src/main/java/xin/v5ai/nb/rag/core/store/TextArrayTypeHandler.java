package xin.v5ai.nb.rag.core.store;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL {@code text[]} ↔ {@code String[]} 类型处理器（关键词分词的索引列 {@code keyword_tokens}）。
 * <p>
 * 写入走 {@link java.sql.Connection#createArrayOf(String, Object[])}（显式声明元素类型为 {@code text}，
 * 避免驱动把 Java 数组当字符串处理）；读取统一转成 {@code String[]}。数据库列为空时返回 {@code null}。
 *
 * @author ZYW
 * @since 2026-09-09
 */
public class TextArrayTypeHandler extends BaseTypeHandler<String[]> {

    /**
     * PG 数组元素类型（与 V31 的 {@code keyword_tokens text[]} 一致）
     */
    private static final String ELEMENT_TYPE = "text";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setArray(i, ps.getConnection().createArrayOf(ELEMENT_TYPE, parameter));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toArray(cs.getArray(columnIndex));
    }

    private static String[] toArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object[] values = (Object[]) array.getArray();
        if (values == null) {
            return null;
        }
        var result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] == null ? null : values[i].toString();
        }
        return result;
    }
}
