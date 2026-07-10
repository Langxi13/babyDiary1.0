package com.langxi.babydiary.tools;

import com.langxi.babydiary.util.ThumbnailGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ThumbnailBackfillTool {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ThumbnailBackfillTool <image-dir>");
            System.exit(2);
        }

        Path imageDir = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(imageDir)) {
            System.err.println("Image directory does not exist: " + imageDir);
            System.exit(2);
        }

        AtomicInteger generated = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        try (Stream<Path> paths = Files.list(imageDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(ThumbnailBackfillTool::isSupportedImage)
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        try {
                            Path thumbnail = imageDir.resolve(ThumbnailGenerator.thumbnailRelativePath(filename));
                            if (Files.exists(thumbnail)) {
                                skipped.incrementAndGet();
                                return;
                            }
                            ThumbnailGenerator.createThumbnail(imageDir, filename);
                            generated.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            System.err.println("thumbnail failed: " + filename + " - " + e.getMessage());
                        }
                    });
        }

        System.out.println("thumbnail backfill generated=" + generated.get()
                + " skipped=" + skipped.get()
                + " failed=" + failed.get());
        if (failed.get() > 0) {
            System.exit(1);
        }
    }

    private static boolean isSupportedImage(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif");
    }
}
