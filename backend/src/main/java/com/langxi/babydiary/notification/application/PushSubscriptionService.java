package com.langxi.babydiary.notification.application;

import com.langxi.babydiary.platform.application.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {
    private final PushSubscriptionRepository subscriptions;
    private final String publicKey;
    private final PushGateway gateway;
    private final boolean validatePublicDns;

    public PushSubscriptionService(
            PushSubscriptionRepository subscriptions,
            @Value("${app.push.vapid-public-key:}") String publicKey,
            PushGateway gateway,
            @Value("${app.push.validate-public-dns:true}") boolean validatePublicDns) {
        this.subscriptions = subscriptions;
        this.publicKey = publicKey;
        this.gateway = gateway;
        this.validatePublicDns = validatePublicDns;
    }

    public String publicKey() {
        return !gateway.configured() || publicKey == null || publicKey.isBlank() ? null : publicKey;
    }

    @Transactional
    public void subscribe(
            long accountId, String endpoint, String p256dh, String auth, String userAgent) {
        validate(endpoint, p256dh, auth);
        subscriptions.save(
                accountId, hash(endpoint), endpoint, p256dh, auth, truncate(userAgent, 500));
    }

    @Transactional
    public void unsubscribe(long accountId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw ApiException.badRequest("PUSH_ENDPOINT_REQUIRED", "推送地址不能为空");
        }
        subscriptions.revoke(accountId, hash(endpoint));
    }

    private void validate(String endpoint, String p256dh, String auth) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 4096) {
            throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送地址无效");
        }
        if (p256dh == null
                || p256dh.isBlank()
                || p256dh.length() > 255
                || auth == null
                || auth.isBlank()
                || auth.length() > 255) {
            throw ApiException.badRequest("PUSH_KEYS_INVALID", "推送密钥无效");
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送地址无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送地址必须使用标准 HTTPS");
        }
        requirePublicHost(uri.getHost());
    }

    private void requirePublicHost(String host) {
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送地址不能指向本机");
        }
        String literal = normalized.replace("[", "").replace("]", "");
        boolean addressLiteral =
                literal.contains(":") || literal.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
        if (!addressLiteral && !validatePublicDns) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(literal)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || isReserved(address)) {
                    throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送地址不能指向内网");
                }
            }
        } catch (java.net.UnknownHostException exception) {
            throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送 IP 地址无效");
        }
    }

    private boolean isReserved(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) return (bytes[0] & 0xfe) == 0xfc;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19));
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }
}
