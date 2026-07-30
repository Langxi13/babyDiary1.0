package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface BackgroundJobHandler {
    String type();

    JsonNode handle(JsonNode payload) throws Exception;
}
