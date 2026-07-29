package com.langxi.babydiary.migration.v3;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class V3DataMigrator {
    private final Path objectRoot;

    V3DataMigrator(Path objectRoot) {
        this.objectRoot = objectRoot;
    }

    List<String> migrate(Connection source, Connection target) throws Exception {
        configureSessions(source, target);
        if (count(target, "account") != 0) throw new IllegalStateException("V3 target already contains account data");

        List<String> completed = new ArrayList<>();
        run(target, "identity", () -> new IdentityDataMigration().migrate(source, target), completed);
        run(target, "diary", () -> new DiaryDataMigration().migrate(source, target), completed);
        run(target, "media", () -> new MediaDataMigration(objectRoot).migrate(source, target), completed);
        run(target, "memory", () -> new MemoryDataMigration().migrate(source, target), completed);
        run(target, "ai", () -> new AiDataMigration().migrate(source, target), completed);
        run(target, "platform", () -> new PlatformDataMigration().migrate(source, target), completed);
        return completed;
    }

    private void configureSessions(Connection source, Connection target) throws SQLException {
        try (Statement sourceStatement = source.createStatement(); Statement targetStatement = target.createStatement()) {
            sourceStatement.execute("SET SESSION time_zone = '+08:00'");
            targetStatement.execute("SET SESSION time_zone = '+00:00'");
            sourceStatement.execute("SET SESSION TRANSACTION READ ONLY");
        }
    }

    private void run(Connection target, String name, MigrationAction action, List<String> completed) throws Exception {
        boolean originalAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try {
            action.run();
            target.commit();
            completed.add(name);
        } catch (Exception exception) {
            target.rollback();
            throw new IllegalStateException("V3 migration step failed: " + name, exception);
        } finally {
            target.setAutoCommit(originalAutoCommit);
        }
    }

    private long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            result.next();
            return result.getLong(1);
        }
    }

    @FunctionalInterface
    private interface MigrationAction {
        void run() throws Exception;
    }
}
