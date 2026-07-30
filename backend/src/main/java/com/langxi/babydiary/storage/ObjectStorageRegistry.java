package com.langxi.babydiary.storage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ObjectStorageRegistry {
    private final Map<String, ObjectStorage> providers;
    private final String writeProvider;

    public ObjectStorageRegistry(
            List<ObjectStorage> storages,
            @Value("${app.storage.provider:local}") String writeProvider) {
        Map<String, ObjectStorage> values = new LinkedHashMap<>();
        for (ObjectStorage storage : storages) {
            String key = normalize(storage.provider());
            if (values.putIfAbsent(key, storage) != null) {
                throw new IllegalStateException("Duplicate object storage provider: " + key);
            }
        }
        this.providers = Map.copyOf(values);
        this.writeProvider = normalize(writeProvider);
        if (!providers.containsKey(this.writeProvider)) {
            throw new IllegalStateException(
                    "Write object storage provider is unavailable: " + this.writeProvider);
        }
    }

    public ObjectStorage writer() {
        return require(writeProvider);
    }

    public ObjectStorage require(String provider) {
        ObjectStorage storage = providers.get(normalize(provider));
        if (storage == null)
            throw new IllegalStateException("Object storage provider is unavailable: " + provider);
        return storage;
    }

    public void verifyReady() throws IOException {
        for (ObjectStorage storage : providers.values()) storage.verifyReady();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
