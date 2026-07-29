package com.langxi.babydiary.migration.v3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

abstract class V3JdbcMigration {
    @FunctionalInterface
    interface Binder {
        void bind(ResultSet source, PreparedStatement target) throws Exception;
    }

    @FunctionalInterface
    interface RowConsumer {
        void accept(ResultSet source) throws Exception;
    }

    protected int copy(Connection source, Connection target, String selectSql, String insertSql, Binder binder) throws Exception {
        int count = 0;
        try (Statement query = source.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rows = query.executeQuery(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            query.setFetchSize(100);
            while (rows.next()) {
                binder.bind(rows, insert);
                insert.addBatch();
                count++;
                if (count % 100 == 0) insert.executeBatch();
            }
            insert.executeBatch();
        }
        return count;
    }

    protected void each(Connection source, String sql, RowConsumer consumer) throws Exception {
        try (Statement query = source.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rows = query.executeQuery(sql)) {
            query.setFetchSize(100);
            while (rows.next()) consumer.accept(rows);
        }
    }

    protected long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("Query returned no row: " + sql);
            return result.getLong(1);
        }
    }

    protected void setLong(PreparedStatement statement, int index, ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    protected void setInt(PreparedStatement statement, int index, ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull()) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    protected void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    protected void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) statement.setNull(index, Types.DATE);
        else statement.setObject(index, value);
    }

    protected void setTime(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP);
        else statement.setString(index, value.toString().replace('T', ' '));
    }

    protected LocalDateTime requiredTime(ResultSet result, String column) throws SQLException {
        LocalDateTime value = V3MigrationSupport.utc(result, column);
        if (value == null) throw new IllegalStateException("Required source timestamp is null: " + column);
        return value;
    }
}
