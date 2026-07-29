package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.StoredObject;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LegacyMediaMigrationService {
    private static final Logger log = LoggerFactory.getLogger(LegacyMediaMigrationService.class);
    static final String APPLY_CONFIRMATION = "UNIFY_MEDIA_V15";
    private static final String SOURCE_SQL = """
            SELECT d.space_id, s.public_id AS space_public_id, d.user_id AS owner_user_id,
                   i.image_path AS legacy_path, 1 AS library_visible, 'LINKED' AS access_scope
            FROM diary_image i
            JOIN diary d ON d.diary_id=i.diary_id
            JOIN diary_space s ON s.space_id=d.space_id
            UNION ALL
            SELECT s.space_id, s.public_id, u.user_id, u.avatar_path, 0, 'PROFILE'
            FROM user u JOIN diary_space s ON s.personal_owner_id=u.user_id
            WHERE u.avatar_path IS NOT NULL AND u.avatar_path<>''
            UNION ALL
            SELECT a.space_id, s.public_id, a.user_id, a.cover_image_path, 0, 'LINKED'
            FROM anniversary a JOIN diary_space s ON s.space_id=a.space_id
            WHERE a.cover_image_path IS NOT NULL AND a.cover_image_path<>''
            UNION ALL
            SELECT a.space_id, s.public_id, a.user_id, a.cover_image_path, 1, 'LINKED'
            FROM album a JOIN diary_space s ON s.space_id=a.space_id
            WHERE a.cover_image_path IS NOT NULL AND a.cover_image_path<>''
            """;

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final ObjectMapper objectMapper;
    private final Path legacyRoot;
    private final String applyConfirmation;

    public LegacyMediaMigrationService(JdbcTemplate jdbc,
                                       ObjectStorage storage,
                                       ObjectMapper objectMapper,
                                       @Value("${diaryFilePath}") String legacyRoot,
                                       @Value("${app.media.migration-confirmation:}") String applyConfirmation) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.legacyRoot = Path.of(legacyRoot).toAbsolutePath().normalize();
        this.applyConfirmation = applyConfirmation;
    }

    public MigrationReport dryRun() {
        List<LegacySource> sources = sources();
        long bytes = 0;
        List<String> failures = new ArrayList<>();
        for (LegacySource source : sources) {
            try {
                Path file = sourcePath(source.legacyPath());
                validateImage(file);
                bytes += Files.size(file);
            } catch (Exception exception) {
                failures.add(source.legacyPath() + ": " + exception.getMessage());
            }
        }
        MigrationReport report = new MigrationReport(sources.size(), bytes, 0, failures);
        report.log("dry-run");
        if (!failures.isEmpty()) throw new IllegalStateException("Legacy media preflight failed");
        return report;
    }

    @Transactional
    public MigrationReport apply() {
        if (!APPLY_CONFIRMATION.equals(applyConfirmation)) {
            throw new IllegalStateException("Media migration apply confirmation is missing or invalid");
        }
        List<LegacySource> sources = sources();
        List<String> writtenKeys = new ArrayList<>();
        int migrated = 0;
        long bytes = 0;
        try {
            for (LegacySource source : sources) {
                Path file = sourcePath(source.legacyPath());
                BufferedImage image = validateImage(file);
                long size = Files.size(file);
                bytes += size;
                String checksum = checksum(file);
                Long existing = mappedAsset(source.spaceId(), source.legacyPath());
                if (existing != null) {
                    verifyMappedAsset(existing, checksum);
                    continue;
                }

                String publicId = UUID.nameUUIDFromBytes(("legacy-media-v1:" + source.spacePublicId()
                        + ":" + source.legacyPath() + ":" + checksum).getBytes(StandardCharsets.UTF_8)).toString();
                String extension = extension(source.legacyPath());
                String contentType = contentType(extension);
                String key = "spaces/" + source.spacePublicId() + "/legacy/" + publicId + extension;
                try (InputStream input = Files.newInputStream(file)) {
                    storage.put(key, input, size, contentType);
                }
                writtenKeys.add(key);

                Path thumbnail = Files.createTempFile("baby-diary-media-migration-", ".jpg");
                String thumbnailKey = key + ".derived/thumbnail.jpg";
                try {
                    Thumbnails.of(image).size(960, 960).outputFormat("jpg").outputQuality(0.84).toFile(thumbnail.toFile());
                    try (InputStream input = Files.newInputStream(thumbnail)) {
                        storage.put(thumbnailKey, input, Files.size(thumbnail), "image/jpeg");
                    }
                    writtenKeys.add(thumbnailKey);
                } finally {
                    Files.deleteIfExists(thumbnail);
                }

                Long assetId = insertAsset(source, publicId, key, thumbnailKey, contentType, size, checksum,
                        image.getWidth(), image.getHeight());
                jdbc.update("INSERT INTO media_legacy_map(space_id,legacy_path,asset_id,checksum_sha256) VALUES(?,?,?,?)",
                        source.spaceId(), source.legacyPath(), assetId, checksum);
                migrated++;
            }

            migrateReferences();
            migrateProposalJson();
            recalculateStorageUsage();
            MigrationReport report = new MigrationReport(sources.size(), bytes, migrated, List.of());
            report.log("apply");
            return report;
        } catch (Exception exception) {
            for (String key : writtenKeys) {
                try {
                    storage.delete(key);
                } catch (IOException cleanupFailure) {
                    log.warn("迁移回滚对象清理失败: key={}, reason={}", key, cleanupFailure.getMessage());
                }
            }
            throw exception instanceof RuntimeException runtime
                    ? runtime : new IllegalStateException("Legacy media migration failed", exception);
        }
    }

    public MigrationReport verify() {
        List<LegacySource> sources = sources();
        List<String> failures = new ArrayList<>();
        long bytes = 0;
        for (LegacySource source : sources) {
            try {
                Path original = sourcePath(source.legacyPath());
                bytes += Files.size(original);
                Map<String, Object> row = jdbc.queryForMap("""
                        SELECT a.asset_id,a.storage_key,a.thumbnail_key,a.checksum_sha256
                        FROM media_legacy_map m JOIN media_asset a ON a.asset_id=m.asset_id
                        WHERE m.space_id=? AND m.legacy_path=? AND a.deleted_at IS NULL
                        """, source.spaceId(), source.legacyPath());
                String expected = checksum(original);
                if (!expected.equals(row.get("checksum_sha256"))) throw new IOException("checksum metadata mismatch");
                try (StoredObject object = storage.get(String.valueOf(row.get("storage_key")))) {
                    if (!expected.equals(checksum(object.stream()))) throw new IOException("stored object checksum mismatch");
                }
                try (StoredObject ignored = storage.get(String.valueOf(row.get("thumbnail_key")))) {
                    // Opening the object verifies that the derivative exists and is readable.
                }
            } catch (Exception exception) {
                failures.add(source.legacyPath() + ": " + exception.getMessage());
            }
        }
        verifyCount(failures, "diary_media", """
                SELECT COUNT(*) FROM diary_image i JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                JOIN diary_media dm ON dm.diary_id=i.diary_id AND dm.asset_id=m.asset_id
                """, "SELECT COUNT(*) FROM diary_image");
        verifyCount(failures, "album_media", """
                SELECT COUNT(*) FROM album_photo ap JOIN diary_image i ON i.image_id=ap.image_id
                JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                JOIN album_media am ON am.album_id=ap.album_id AND am.asset_id=m.asset_id
                """, "SELECT COUNT(*) FROM album_photo");
        verifyCount(failures, "favorite_media", """
                SELECT COUNT(*) FROM favorite_photo fp JOIN diary_image i ON i.image_id=fp.image_id
                JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                JOIN favorite_media fm ON fm.user_id=fp.user_id AND fm.asset_id=m.asset_id
                """, "SELECT COUNT(*) FROM favorite_photo");
        MigrationReport report = new MigrationReport(sources.size(), bytes, sources.size(), failures);
        report.log("verify");
        if (!failures.isEmpty()) throw new IllegalStateException("Legacy media verification failed");
        return report;
    }

    private List<LegacySource> sources() {
        Map<String, LegacySource> merged = new LinkedHashMap<>();
        jdbc.query(SOURCE_SQL, result -> {
            LegacySource next = new LegacySource(
                    result.getLong("space_id"),
                    result.getString("space_public_id"),
                    result.getInt("owner_user_id"),
                    result.getString("legacy_path"),
                    result.getBoolean("library_visible"),
                    result.getString("access_scope"));
            String key = next.spaceId() + "\0" + next.legacyPath();
            merged.merge(key, next, (left, right) -> new LegacySource(
                    left.spaceId(), left.spacePublicId(), left.ownerUserId(), left.legacyPath(),
                    left.libraryVisible() || right.libraryVisible(),
                    "PROFILE".equals(left.accessScope()) || "PROFILE".equals(right.accessScope())
                            ? "PROFILE" : "LINKED"));
        });
        return new ArrayList<>(merged.values());
    }

    private Long mappedAsset(Long spaceId, String legacyPath) {
        List<Long> values = jdbc.query("SELECT asset_id FROM media_legacy_map WHERE space_id=? AND legacy_path=?",
                (result, row) -> result.getLong(1), spaceId, legacyPath);
        return values.isEmpty() ? null : values.get(0);
    }

    private void verifyMappedAsset(Long assetId, String checksum) {
        String stored = jdbc.queryForObject("SELECT checksum_sha256 FROM media_asset WHERE asset_id=? AND deleted_at IS NULL",
                String.class, assetId);
        if (!checksum.equals(stored)) throw new IllegalStateException("Mapped legacy asset checksum changed: " + assetId);
    }

    private Long insertAsset(LegacySource source, String publicId, String key, String thumbnailKey,
                             String contentType, long size, String checksum, int width, int height) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO media_asset(public_id,space_id,owner_user_id,media_type,original_filename,
                      storage_provider,storage_key,thumbnail_key,content_type,size_bytes,checksum_sha256,
                      access_scope,library_visible,width,height,status)
                    VALUES(?,?,?,?,?,'LOCAL',?,?,?,?,?,?,?,?,?,'READY')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, publicId);
            statement.setLong(2, source.spaceId());
            statement.setInt(3, source.ownerUserId());
            statement.setString(4, "IMAGE");
            statement.setString(5, Path.of(source.legacyPath()).getFileName().toString());
            statement.setString(6, key);
            statement.setString(7, thumbnailKey);
            statement.setString(8, contentType);
            statement.setLong(9, size);
            statement.setString(10, checksum);
            statement.setString(11, source.accessScope());
            statement.setBoolean(12, source.libraryVisible());
            statement.setInt(13, width);
            statement.setInt(14, height);
            return statement;
        }, holder);
        Number keyValue = holder.getKey();
        if (keyValue == null) throw new IllegalStateException("Media asset insert did not return an ID");
        return keyValue.longValue();
    }

    private void migrateReferences() {
        jdbc.update("""
                INSERT IGNORE INTO diary_media(diary_id,asset_id,sort,created_at)
                SELECT i.diary_id,m.asset_id,i.sort,COALESCE(d.created_at,CURRENT_TIMESTAMP)
                FROM diary_image i JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                """);
        jdbc.update("""
                INSERT IGNORE INTO album_media(album_id,asset_id,sort,created_at)
                SELECT ap.album_id,m.asset_id,ap.sort,COALESCE(ap.created_at,CURRENT_TIMESTAMP)
                FROM album_photo ap JOIN diary_image i ON i.image_id=ap.image_id
                JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                """);
        jdbc.update("""
                INSERT IGNORE INTO favorite_media(user_id,asset_id,created_at)
                SELECT fp.user_id,m.asset_id,COALESCE(fp.created_at,CURRENT_TIMESTAMP)
                FROM favorite_photo fp JOIN diary_image i ON i.image_id=fp.image_id
                JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                """);
        jdbc.update("""
                INSERT INTO user_avatar(user_id,asset_id)
                SELECT u.user_id,m.asset_id FROM user u
                JOIN diary_space s ON s.personal_owner_id=u.user_id
                JOIN media_legacy_map m ON m.space_id=s.space_id AND m.legacy_path=u.avatar_path
                WHERE u.avatar_path IS NOT NULL AND u.avatar_path<>''
                ON DUPLICATE KEY UPDATE asset_id=VALUES(asset_id)
                """);
        jdbc.update("""
                UPDATE anniversary a JOIN media_legacy_map m
                  ON m.space_id=a.space_id AND m.legacy_path=a.cover_image_path
                SET a.cover_asset_id=m.asset_id
                WHERE a.cover_image_path IS NOT NULL AND a.cover_image_path<>''
                """);
        jdbc.update("""
                UPDATE album a JOIN media_legacy_map m
                  ON m.space_id=a.space_id AND m.legacy_path=a.cover_image_path
                SET a.cover_asset_id=m.asset_id
                WHERE a.cover_image_path IS NOT NULL AND a.cover_image_path<>''
                """);
    }

    private void migrateProposalJson() {
        Map<Integer, String> assetByLegacyImage = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT i.image_id,a.public_id FROM diary_image i JOIN diary d ON d.diary_id=i.diary_id
                JOIN media_legacy_map m ON m.space_id=d.space_id AND m.legacy_path=i.image_path
                JOIN media_asset a ON a.asset_id=m.asset_id
                """).forEach(row -> assetByLegacyImage.put(
                ((Number) row.get("image_id")).intValue(), String.valueOf(row.get("public_id"))));
        List<Map<String, Object>> proposals = jdbc.queryForList(
                "SELECT proposal_id,content_json FROM ai_album_proposal");
        for (Map<String, Object> proposal : proposals) {
            int proposalId = ((Number) proposal.get("proposal_id")).intValue();
            try {
                JsonNode root = objectMapper.readTree(String.valueOf(proposal.get("content_json")));
                JsonNode albums = root.path("albums");
                if (!albums.isArray()) continue;
                boolean changed = false;
                for (JsonNode value : albums) {
                    if (!(value instanceof ObjectNode album) || !album.path("imageIds").isArray()) continue;
                    ArrayNode assetIds = objectMapper.createArrayNode();
                    for (JsonNode imageId : album.path("imageIds")) {
                        String assetId = assetByLegacyImage.get(imageId.asInt());
                        if (assetId != null) assetIds.add(assetId);
                    }
                    album.set("assetIds", assetIds);
                    album.remove("imageIds");
                    changed = true;
                }
                if (changed) {
                    jdbc.update("UPDATE ai_album_proposal SET content_json=? WHERE proposal_id=?",
                            objectMapper.writeValueAsString(root), proposalId);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to migrate AI album proposal " + proposalId, exception);
            }
        }
    }

    private void recalculateStorageUsage() {
        jdbc.update("""
                UPDATE space_storage_usage u
                SET used_bytes=(SELECT COALESCE(SUM(a.size_bytes),0) FROM media_asset a
                                WHERE a.space_id=u.space_id AND a.deleted_at IS NULL)
                """);
    }

    private void verifyCount(List<String> failures, String label, String migratedSql, String legacySql) {
        Integer migrated = jdbc.queryForObject(migratedSql, Integer.class);
        Integer legacy = jdbc.queryForObject(legacySql, Integer.class);
        if (!java.util.Objects.equals(migrated, legacy)) {
            failures.add(label + " count mismatch: legacy=" + legacy + ", migrated=" + migrated);
        }
    }

    private Path sourcePath(String legacyPath) {
        Path relative = Path.of(legacyPath).normalize();
        Path source = legacyRoot.resolve(relative).normalize();
        if (relative.isAbsolute() || !source.startsWith(legacyRoot)) {
            throw new IllegalArgumentException("Legacy path escapes media root");
        }
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("File is missing");
        return source;
    }

    private BufferedImage validateImage(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            throw new IOException("Unsupported or corrupt image");
        }
        return image;
    }

    private String checksum(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            return checksum(input);
        }
    }

    private String checksum(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        return HexFormat.of().formatHex(digest.digest());
    }

    private String extension(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpeg")) return ".jpg";
        for (String extension : List.of(".jpg", ".png", ".gif", ".webp")) {
            if (lower.endsWith(extension)) return extension;
        }
        throw new IllegalArgumentException("Unsupported image extension");
    }

    private String contentType(String extension) {
        return switch (extension) {
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private record LegacySource(Long spaceId, String spacePublicId, Integer ownerUserId, String legacyPath,
                                boolean libraryVisible, String accessScope) {
    }

    public record MigrationReport(int referencedFiles, long bytes, int migratedFiles, List<String> failures) {
        void log(String mode) {
            LegacyMediaMigrationService.log.info(
                    "媒体迁移 {}: referenced={}, bytes={}, migrated={}, failures={}",
                    mode, referencedFiles, bytes, migratedFiles, failures.size());
            failures.forEach(failure -> LegacyMediaMigrationService.log.error("媒体迁移校验失败: {}", failure));
        }
    }
}
