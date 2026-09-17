package com.anfioo.howtocook.common.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * LocalDateTime 类型处理器（PostgreSQL 专用）。
 * <p>背景：PG JDBC 对 {@code timestamptz} 列不支持 {@code getObject(x, LocalDateTime.class)}
 * （仅支持 OffsetDateTime），而 MyBatis 默认的 LocalDateTimeTypeHandler 正是这么取值，
 * 导致读列报错 "Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime"。
 * 本处理器改走 {@code getTimestamp().toLocalDateTime()}，对 timestamp / timestamptz 均可用。</p>
 */
@MappedTypes(LocalDateTime.class)
public class PGLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnIndex);
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp ts = cs.getTimestamp(columnIndex);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
