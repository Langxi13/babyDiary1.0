package com.langxi.babydiary.identity.infrastructure;

import com.langxi.babydiary.identity.application.AccessTokenCodec;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.AuthenticationProjectionCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokenCodec tokens;
    private final AuthenticationProjectionCache accounts;

    public AuthenticationFilter(AccessTokenCodec tokens, AuthenticationProjectionCache accounts) {
        this.tokens = tokens;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokens.decode(authorization.substring(7).trim())
                    .filter(token -> token.expiresAt().isAfter(Instant.now()))
                    .flatMap(token -> accounts.find(token.accountId(), token.tokenVersion()))
                    .ifPresent(
                            account -> {
                                AccountPrincipal principal =
                                        new AccountPrincipal(
                                                account.accountId(),
                                                account.publicId(),
                                                account.username(),
                                                account.systemRole());
                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                principal,
                                                null,
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_" + account.systemRole())));
                                SecurityContextHolder.getContext()
                                        .setAuthentication(authentication);
                            });
        }
        filterChain.doFilter(request, response);
    }
}
