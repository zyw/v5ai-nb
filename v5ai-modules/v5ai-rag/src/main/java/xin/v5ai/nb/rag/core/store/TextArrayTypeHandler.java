package xin.v5ai.nb.rag.core.store;

import cn.hutool.json.JSONUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 分词列 {@code keyword_tokens} ↔ {@code String[]} 类型处理器。
 * <p>
 * 类名保留 {@code TextArray} 是历史原因（该列最初只有 PostgreSQL 的 {@code text[]} 形态）。
 * 业务库支持 MySQL 之后（见 docs/adr/0012），同一列在两方言是不同物理类型，本处理器按连接
 * 自动识别并互转，调用方（实体注解与 mapper XML）无需感知：
 * <ul>
 *   <li>PostgreSQL：{@code text[]} —— 写入走 {@link Connection#createArrayOf(String, Object[])}
 *       （显式声明元素类型为 {@code text}，避免驱动把 Java 数组当字符串处理），读取走 {@code getArray}；</li>
 *   <li>MySQL：{@code JSON}（字符串数组）—— 写入为 JSON 文本串（JSON 列直接接收字符串），
 *       读取把列值按 JSON 数组解析。检索侧对应的 SQL 分支见 {@code KnowledgeChunkMapper.xml}。</li>
 * </ul>
 * 列值为空时返回 {@code null}。
 * <p>
 * 方言判定取连接元数据的产品名，每次调用现取、不缓存：两家驱动的
 * {@code getDatabaseProductName()} 都从连接自身的缓存读（不发 SQL），
 * 而进程级缓存会在「同 JVM 内先跑 PG 再跑 MySQL」时直接判错（单测就是这个形状）。
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
        if (isMySql(ps.getConnection())) {
            ps.setString(i, JSONUtil.toJsonStr(parameter));
            return;
        }
        ps.setArray(i, ps.getConnection().createArrayOf(ELEMENT_TYPE, parameter));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toTokens(rs.getObject(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toTokens(rs.getObject(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toTokens(cs.getObject(columnIndex));
    }

    private static boolean isMySql(Connection connection) throws SQLException {
        return connection != null
                && "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }

    /**
     * 列值 → String[]：PG 给 {@link java.sql.Array}，MySQL 的 JSON 列给 JSON 文本串。
     */
    private static String[] toTokens(Object raw) throws SQLException {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Array array) {
            return toArray(array);
        }
        String json = raw.toString();
        if (json.isBlank() || "null".equals(json)) {
            return null;
        }
        var list = JSONUtil.parseArray(json);
        var result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            result[i] = element == null ? null : element.toString();
        }
        return result;
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
