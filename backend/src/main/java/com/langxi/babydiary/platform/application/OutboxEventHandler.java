package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public interface OutboxEventHandler {
    Set<String> eventTypes();

    JsonNode handle(OutboxEventRepository.Event event) throws Exception;
}
