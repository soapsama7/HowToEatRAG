package com.anfioo.howtocook.common.typehandler;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * pgvector 类型处理器：Java {@link PGvector}（继承 PGobject）↔ PostgreSQL {@code vector} 列。
 * <p>写入时直接 setObject（PGobject 携带 "vector" 类型标识）；
 * 读取时按字符串 "[1,2,3]" 解析回 PGvector。</p>
 */
@MappedTypes(PGvector.class)
public class VectorTypeHandler extends BaseTypeHandler<PGvector> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PGvector parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public PGvector getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public PGvector getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public PGvector getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private PGvector parse(String value) throws SQLException {
        return value == null ? null : new PGvector(value);
    }
}
