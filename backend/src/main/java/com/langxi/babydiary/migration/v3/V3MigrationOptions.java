package com.langxi.babydiary.migration.v3;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

record V3MigrationOptions(
        Command command,
        Database source,
        Database target,
        Path objectRoot,
        boolean confirmed
) {
    static final String CONFIRMATION = "MIGRATE_TO_V3";

    enum Command {
        PREFLIGHT,
        MIGRATE,
        VERIFY
    }

    record Database(String url, String username, String password) {
    }

    static V3MigrationOptions parse(String[] args, Map<String, String> environment) {
        if (args.length == 0) throw usage("A command is required");
        Command command;
        try {
            command = Command.valueOf(args[0].trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw usage("Unknown command: " + args[0]);
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) throw usage("Unexpected argument: " + argument);
            int equals = argument.indexOf('=');
            if (equals <= 2) throw usage("Options must use --name=value syntax: " + argument);
            values.put(argument.substring(2, equals), argument.substring(equals + 1));
        }

        Database source = database(values, environment, "source", "V3_SOURCE");
        Database target = command == Command.PREFLIGHT && missing(values, environment, "target-url", "V3_TARGET_URL")
                ? null : database(values, environment, "target", "V3_TARGET");
        String objectRoot = value(values, environment, "object-root", "V3_OBJECT_ROOT", "data/objects");
        String confirmation = value(values, environment, "confirm", "V3_MIGRATION_CONFIRM", "");
        return new V3MigrationOptions(command, source, target, Path.of(objectRoot).toAbsolutePath().normalize(),
                CONFIRMATION.equals(confirmation));
    }

    private static Database database(Map<String, String> values, Map<String, String> environment,
                                     String optionPrefix, String environmentPrefix) {
        String url = required(values, environment, optionPrefix + "-url", environmentPrefix + "_URL");
        String username = required(values, environment, optionPrefix + "-user", environmentPrefix + "_USER");
        String password = required(values, environment, optionPrefix + "-password", environmentPrefix + "_PASSWORD");
        return new Database(url, username, password);
    }

    private static String required(Map<String, String> values, Map<String, String> environment,
                                   String option, String environmentName) {
        String value = value(values, environment, option, environmentName, null);
        if (value == null || value.isBlank()) throw usage("Missing --" + option + " or " + environmentName);
        return value;
    }

    private static String value(Map<String, String> values, Map<String, String> environment,
                                String option, String environmentName, String fallback) {
        String optionValue = values.get(option);
        if (optionValue != null) return optionValue;
        return environment.getOrDefault(environmentName, fallback);
    }

    private static boolean missing(Map<String, String> values, Map<String, String> environment,
                                   String option, String environmentName) {
        String value = value(values, environment, option, environmentName, null);
        return value == null || value.isBlank();
    }

    static IllegalArgumentException usage(String message) {
        return new IllegalArgumentException(message + System.lineSeparator()
                + "Usage: V3MigrationCli <preflight|migrate|verify> "
                + "--source-url=... --source-user=... --source-password=... "
                + "[--target-url=... --target-user=... --target-password=...] "
                + "[--object-root=...] [--confirm=" + CONFIRMATION + "]");
    }
}
