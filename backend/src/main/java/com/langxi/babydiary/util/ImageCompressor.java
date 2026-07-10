package com.langxi.babydiary.util;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageCompressor {

    // 最大文件大小，小于此大小的图片不压缩，这里是300KB
    private static final long MAX_FILE_SIZE = 300 * 1024;
    private static final float QUALITY = 0.85f;

    public static byte[] compressImage(MultipartFile file) throws IOException {
        return compressImage(file.getBytes(), file.getContentType());
    }

    public static byte[] compressImage(byte[] originalBytes, String contentType) throws IOException {
        
        if (contentType == null || !contentType.startsWith("image/")) {
            return originalBytes;
        }

        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (originalImage == null) {
            return originalBytes;
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        int maxDimension = 1920;
        int targetWidth = originalWidth;
        int targetHeight = originalHeight;

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                targetWidth = maxDimension;
                targetHeight = (int) ((originalHeight * maxDimension) / (double) originalWidth);
            } else {
                targetHeight = maxDimension;
                targetWidth = (int) ((originalWidth * maxDimension) / (double) originalHeight);
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        String formatName = getFormatName(contentType);
        
        Thumbnails.of(originalImage)
                .size(targetWidth, targetHeight)
                .outputFormat(formatName)
                .outputQuality(QUALITY)
                .toOutputStream(outputStream);

        byte[] compressedBytes = outputStream.toByteArray();

        if (compressedBytes.length < originalBytes.length) {
            return compressedBytes;
        }

        return originalBytes;
    }

    private static String getFormatName(String contentType) {
        if (contentType.contains("png")) {
            return "png";
        } else if (contentType.contains("gif")) {
            return "gif";
        }
        return "jpg";
    }

    public static boolean shouldCompress(MultipartFile file) {
        return file.getSize() > MAX_FILE_SIZE;
    }
}
