package com.langxi.babydiary.storage;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("objectStorage")
public class ObjectStorageHealthIndicator implements HealthIndicator {
    private final ObjectStorageRegistry storages;

    public ObjectStorageHealthIndicator(ObjectStorageRegistry storages) {
        this.storages = storages;
    }

    @Override
    public Health health() {
        try {
            storages.verifyReady();
            return Health.up().build();
        } catch (Exception exception) {
            return Health.down().withException(exception).build();
        }
    }
}
