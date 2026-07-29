package com.langxi.babydiary.tools;

import com.langxi.babydiary.service.LegacyMediaMigrationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.media.migration-mode")
public class LegacyMediaMigrationRunner implements ApplicationRunner {
    private final LegacyMediaMigrationService migrationService;
    private final ConfigurableApplicationContext context;
    private final String mode;

    public LegacyMediaMigrationRunner(LegacyMediaMigrationService migrationService,
                                      ConfigurableApplicationContext context,
                                      org.springframework.core.env.Environment environment) {
        this.migrationService = migrationService;
        this.context = context;
        this.mode = environment.getRequiredProperty("app.media.migration-mode");
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (mode) {
            case "dry-run" -> migrationService.dryRun();
            case "apply" -> migrationService.apply();
            case "verify" -> migrationService.verify();
            default -> throw new IllegalArgumentException("Unsupported media migration mode: " + mode);
        }
        SpringApplication.exit(context);
    }
}
