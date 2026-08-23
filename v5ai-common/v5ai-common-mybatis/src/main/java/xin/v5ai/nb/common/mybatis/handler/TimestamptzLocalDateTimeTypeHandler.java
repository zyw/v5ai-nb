//package xin.v5ai.nb.common.mybatis.handler;
//
//import org.apache.ibatis.type.BaseTypeHandler;
//import org.apache.ibatis.type.JdbcType;
//import org.apache.ibatis.type.MappedTypes;
//
//import java.sql.CallableStatement;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.time.LocalDateTime;
//
///**
// * 兼容 PostgreSQL {@code TIMESTAMPTZ} 列的 {@link LocalDateTime} 处理器。
// *
// * <p>PG JDBC 不允许把 {@code timestamptz} 列直接反序列化为 {@link LocalDateTime}
// * （报 {@code Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime}），
// * 本处理器改用 {@code getTimestamp()} 读取（按 JVM 时区解释）再转 {@link LocalDateTime}，
// * 与 {@code BaseEntity.createdAt/updatedAt} 的写入语义（{@code LocalDateTime.now()}）闭环。
// * 注册后覆盖 MyBatis 默认的 {@code LocalDateTimeTypeHandler}，对全部业务模块生效。</p>
// *
// * @author ZYW
// * @since 2026-08-23
// */
//@MappedTypes(LocalDateTime.class)
//public class TimestamptzLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {
//
//    @Override
//    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
//            throws SQLException {
//        // timestamptz 列写入：由 PG 服务端按会话时区将 timestamp 隐式转为 timestamptz
//        ps.setObject(i, parameter);
//    }
//
//    @Override
//    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
//        var timestamp = rs.getTimestamp(columnName);
//        return timestamp == null ? null : timestamp.toLocalDateTime();
//    }
//
//    @Override
//    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
//        var timestamp = rs.getTimestamp(columnIndex);
//        return timestamp == null ? null : timestamp.toLocalDateTime();
//    }
//
//    @Override
//    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
//        var timestamp = cs.getTimestamp(columnIndex);
//        return timestamp == null ? null : timestamp.toLocalDateTime();
//    }
//}
