package com.langxi.babydiary.v3.identity.application;

import com.langxi.babydiary.v3.identity.domain.Account;

import java.time.Instant;
import java.util.Optional;

public interface AccessTokenCodec {
    IssuedToken issue(Account account);

    Optional<DecodedToken> decode(String token);

    record IssuedToken(String value, Instant expiresAt) {
    }

    record DecodedToken(long accountId, int tokenVersion, Instant expiresAt) {
    }
}
