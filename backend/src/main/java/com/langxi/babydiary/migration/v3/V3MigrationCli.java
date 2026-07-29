package com.langxi.babydiary.migration.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class V3MigrationCli {
    private static final ObjectMapper JSON = new ObjectMapper();

    private V3MigrationCli() {
    }

    public static void main(String[] args) {
        try {
            V3MigrationOptions options = V3MigrationOptions.parse(args, System.getenv());
            V3MigrationReport report = execute(options);
            System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            if (!report.valid()) System.exit(2);
        } catch (Exception exception) {
            System.err.println("V3 migration failed: " + rootMessage(exception));
            System.exit(1);
        }
    }

    public static V3MigrationReport executeForTest(String[] args) throws Exception {
        return execute(V3MigrationOptions.parse(args, Map.of()));
    }

    static V3MigrationReport execute(V3MigrationOptions options) throws Exception {
        try (Connection source = connect(options.source())) {
            source.setReadOnly(true);
            setTimeZone(source, "+08:00");
            V3MigrationReport preflight = new V3MigrationPreflight().inspect(
                    source, options.objectRoot(), true);
            if (options.command() == V3MigrationOptions.Command.PREFLIGHT) return preflight;
            if (!preflight.valid()) return preflight;
            if (options.target() == null) throw V3MigrationOptions.usage("Target database is required");
            if (sameDatabase(options.source(), options.target())) throw new IllegalArgumentException("Source and target database must be different");

            if (options.command() == V3MigrationOptions.Command.MIGRATE) {
                if (!options.confirmed()) throw new IllegalArgumentException(
                        "Migration requires --confirm=" + V3MigrationOptions.CONFIRMATION);
                requireEmptyTarget(options.target());
                migrateSchema(options.target());
                try (Connection target = connect(options.target())) {
                    setTimeZone(target, "+00:00");
                    List<String> completed = new V3DataMigrator(options.objectRoot()).migrate(source, target);
                    V3MigrationReport verified = new V3MigrationVerifier().verify(source, target);
                    List<String> checks = new ArrayList<>(verified.checks());
                    completed.forEach(step -> checks.add("migration-step:" + step));
                    return new V3MigrationReport("migrate", verified.valid(), verified.counts(), checks, verified.failures());
                }
            }

            try (Connection target = connect(options.target())) {
                setTimeZone(target, "+00:00");
                return new V3MigrationVerifier().verify(source, target);
            }
        }
    }

    private static void migrateSchema(V3MigrationOptions.Database target) {
        Flyway.configure()
                .dataSource(target.url(), target.username(), target.password())
                .locations("classpath:db/v3/migration")
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static void requireEmptyTarget(V3MigrationOptions.Database target) throws Exception {
        try (Connection connection = connect(target); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
            result.next();
            if (result.getLong(1) != 0) throw new IllegalStateException("Target database must be empty before migrate");
        }
    }

    private static Connection connect(V3MigrationOptions.Database database) throws Exception {
        return DriverManager.getConnection(database.url(), database.username(), database.password());
    }

    private static void setTimeZone(Connection connection, String timeZone) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION time_zone='" + timeZone + "'");
        }
    }

    private static boolean sameDatabase(V3MigrationOptions.Database source, V3MigrationOptions.Database target) {
        return source.url().equalsIgnoreCase(target.url())
                && source.username().equalsIgnoreCase(target.username());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
