package com.langxi.babydiary.util;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class ThumbnailGenerator {

    public static final int THUMBNAIL_SIZE = 480;
    public static final String THUMBNAIL_ROOT = "thumbs/480";

    private ThumbnailGenerator() {
    }

    public static Path createThumbnail(Path uploadRoot, String imagePath) throws IOException {
        Path source = resolveImagePath(uploadRoot, imagePath);
        Path target = resolveThumbnailPath(uploadRoot, imagePath);
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            return target;
        }

        Files.createDirectories(target.getParent());
        int targetWidth = image.getWidth();
        int targetHeight = image.getHeight();
        int largestSide = Math.max(targetWidth, targetHeight);
        if (largestSide > THUMBNAIL_SIZE) {
            double scale = THUMBNAIL_SIZE / (double) largestSide;
            targetWidth = Math.max(1, (int) Math.round(targetWidth * scale));
            targetHeight = Math.max(1, (int) Math.round(targetHeight * scale));
        }

        Thumbnails.of(image)
                .size(targetWidth, targetHeight)
                .outputFormat(formatName(imagePath))
                .outputQuality(0.82f)
                .toFile(target.toFile());
        return target;
    }

    public static void deleteThumbnail(Path uploadRoot, String imagePath) throws IOException {
        Files.deleteIfExists(resolveThumbnailPath(uploadRoot, imagePath));
    }

    public static String thumbnailRelativePath(String imagePath) {
        return THUMBNAIL_ROOT + "/" + safeRelativePath(imagePath);
    }

    public static boolean isThumbnailPath(Path path) {
        return path.normalize().toString().replace('\\', '/').contains("/" + THUMBNAIL_ROOT + "/");
    }

    private static Path resolveImagePath(Path uploadRoot, String imagePath) {
        Path root = uploadRoot.toAbsolutePath().normalize();
        Path path = root.resolve(safeRelativePath(imagePath)).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Image path escapes upload directory");
        }
        return path;
    }

    private static Path resolveThumbnailPath(Path uploadRoot, String imagePath) {
        Path root = uploadRoot.toAbsolutePath().normalize();
        Path path = root.resolve(thumbnailRelativePath(imagePath)).normalize();
        if (!path.startsWith(root.resolve(THUMBNAIL_ROOT).normalize())) {
            throw new IllegalArgumentException("Thumbnail path escapes upload directory");
        }
        return path;
    }

    private static String safeRelativePath(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Image path is required");
        }
        try {
            Path path = Paths.get(imagePath).normalize();
            if (path.isAbsolute() || path.startsWith("..") || path.toString().contains("..")) {
                throw new IllegalArgumentException("Image path escapes upload directory");
            }
            return path.toString().replace('\\', '/');
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid image path", e);
        }
    }

    private static String formatName(String imagePath) {
        String lower = imagePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "png";
        }
        if (lower.endsWith(".gif")) {
            return "gif";
        }
        return "jpg";
    }
}
