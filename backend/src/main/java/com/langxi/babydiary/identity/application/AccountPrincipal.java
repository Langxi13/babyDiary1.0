package com.langxi.babydiary.identity.application;

import java.security.Principal;
import java.util.UUID;

public record AccountPrincipal(long accountId, UUID publicId, String username, String role)
        implements Principal {
    @Override
    public String getName() {
        return publicId.toString();
    }
}
