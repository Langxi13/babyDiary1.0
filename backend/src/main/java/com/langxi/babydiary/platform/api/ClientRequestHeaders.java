package com.langxi.babydiary.platform.api;

import java.util.List;

public final class ClientRequestHeaders {
    public static final String PLATFORM = "X-Client-Platform";
    public static final String VERSION_CODE = "X-Client-Version-Code";
    public static final String VERSION_NAME = "X-Client-Version-Name";
    public static final String DEVICE_NAME = "X-Device-Name";
    public static final String STEP_UP_TOKEN = "X-Step-Up-Token";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    public static final List<String> CORS_ALLOWED =
            List.of(
                    "Authorization",
                    "Content-Type",
                    "If-Match",
                    IDEMPOTENCY_KEY,
                    DEVICE_NAME,
                    STEP_UP_TOKEN,
                    PLATFORM,
                    VERSION_CODE,
                    VERSION_NAME);

    private ClientRequestHeaders() {}
}
