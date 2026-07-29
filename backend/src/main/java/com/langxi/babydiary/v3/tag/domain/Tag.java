package com.langxi.babydiary.v3.tag.domain;

import java.util.UUID;

public record Tag(long internalId, UUID id, UUID spaceId, String name, String color) {
}
