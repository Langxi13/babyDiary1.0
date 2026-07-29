package com.langxi.babydiary.v3.identity.application;

import java.security.Principal;
import java.util.UUID;

public record V3Principal(long accountId, UUID publicId, String username, String role) implements Principal {
    @Override
    public String getName() {
        return publicId.toString();
    }
}
