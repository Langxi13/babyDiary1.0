package com.langxi.babydiary.identity.domain;

import java.util.UUID;

public record Account(
        long id,
        UUID publicId,
        String username,
        String passwordHash,
        String email,
        boolean emailVerified,
        String systemRole,
        String timezone,
        int tokenVersion,
        String status
) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
