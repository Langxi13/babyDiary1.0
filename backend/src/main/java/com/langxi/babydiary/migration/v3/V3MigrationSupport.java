package com.langxi.babydiary.migration.v3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;

final class V3MigrationSupport {
    static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Shanghai");
    static final ZoneId TARGET_ZONE = ZoneOffset.UTC;
    static final ObjectMapper JSON = new ObjectMapper();

    private V3MigrationSupport() {
    }

    static byte[] uuid(String namespace, String name) {
        try {
            byte[] namespaceBytes = uuidBytes(UUID.fromString(namespace));
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            byte[] input = new byte[namespaceBytes.length + nameBytes.length];
            System.arraycopy(namespaceBytes, 0, input, 0, namespaceBytes.length);
            System.arraycopy(nameBytes, 0, input, namespaceBytes.length, nameBytes.length);
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(input);
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x50);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            byte[] result = new byte[16];
            System.arraycopy(digest, 0, result, 0, result.length);
            return result;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }

    static byte[] parseUuid(String value, String namespace, String fallbackName) {
        if (value != null && !value.isBlank()) {
            try {
                return uuidBytes(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Legacy rows may contain non-UUID entity references. They receive a stable UUIDv5.
            }
        }
        return uuid(namespace, fallbackName);
    }

    static byte[] uuidBytes(UUID value) {
        long most = value.getMostSignificantBits();
        long least = value.getLeastSignificantBits();
        byte[] result = new byte[16];
        for (int index = 0; index < 8; index++) {
            result[index] = (byte) (most >>> (56 - index * 8));
            result[index + 8] = (byte) (least >>> (56 - index * 8));
        }
        return result;
    }

    static String uuidString(byte[] value) {
        if (value == null || value.length != 16) throw new IllegalArgumentException("UUID bytes must contain 16 bytes");
        long most = 0;
        long least = 0;
        for (int index = 0; index < 8; index++) most = (most << 8) | (value[index] & 0xffL);
        for (int index = 8; index < 16; index++) least = (least << 8) | (value[index] & 0xffL);
        return new UUID(most, least).toString();
    }

    static byte[] hexBytes(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() != 64 || !normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("Expected a SHA-256 hex value");
        }
        return HexFormat.of().parseHex(normalized);
    }

    static String hex(byte[] bytes) {
        return bytes == null ? null : HexFormat.of().formatHex(bytes);
    }

    static LocalDateTime utc(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) return null;
        LocalDateTime source = LocalDateTime.parse(value.trim().replace(' ', 'T'));
        return source.atZone(SOURCE_ZONE).withZoneSameInstant(TARGET_ZONE).toLocalDateTime();
    }

    static LocalDateTime targetUtc(ResultSet result, int column) throws SQLException {
        String value = result.getString(column);
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value.trim().replace(' ', 'T'));
    }

    static LocalDate date(ResultSet result, String column) throws SQLException {
        java.sql.Date value = result.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    static String plainToHtml(String value) {
        if (value == null || value.isEmpty()) return "<p></p>";
        String escaped = Entities.escape(value);
        return "<p>" + escaped.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>") + "</p>";
    }

    static String textFromHtml(String html) {
        return html == null ? "" : Jsoup.parseBodyFragment(html).text();
    }

    static String contentHtml(String content, String format) {
        return "html".equalsIgnoreCase(format) ? (content == null ? "<p></p>" : content) : plainToHtml(content);
    }

    static String contentText(String content, String format, String html) {
        if (!"html".equalsIgnoreCase(format)) return content == null ? "" : content;
        return textFromHtml(html);
    }

    static String status(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    static String jsonOrEmptyObject(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            JsonNode node = JSON.readTree(value);
            return node == null || !node.isObject() ? "{}" : JSON.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    static ObjectNode object() {
        return JSON.createObjectNode();
    }

    static ArrayNode array() {
        return JSON.createArrayNode();
    }

    static long fileSize(Path root, String provider, String key, boolean required) throws IOException {
        if (!"LOCAL".equalsIgnoreCase(provider) || key == null || key.isBlank()) return 0L;
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root.normalize())) throw new IOException("Storage key escapes object root");
        if (!Files.isRegularFile(file)) {
            if (required) throw new IOException("Missing local media object: " + key);
            return 0L;
        }
        return Files.size(file);
    }

    static byte[] fileSha256(Path root, String provider, String key, boolean required) throws IOException {
        if (!"LOCAL".equalsIgnoreCase(provider) || key == null || key.isBlank()) return null;
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root.normalize())) throw new IOException("Storage key escapes object root");
        if (!Files.isRegularFile(file)) {
            if (required) throw new IOException("Missing local media object: " + key);
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
                return digest.digest();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }

    static String stringify(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize JSON", exception);
        }
    }
}
