package io.github.langxi13.babydiary;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;

@CapacitorPlugin(name = "NativeShareReceiver")
public class NativeShareReceiverPlugin extends Plugin {
    private static final String TAG = "NativeShareReceiver";
    private static final int MAX_FILES = 20;
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_IMAGE_DIMENSION = 1920;
    private static final int JPEG_QUALITY = 85;
    private static final long MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L;
    private static final String EVENT_SHARE_AVAILABLE = "shareAvailable";
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private Intent pendingIntent;

    @Override
    public void load() {
        pendingIntent = shareIntent(getActivity().getIntent());
        cleanupExpiredFiles();
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        Intent shared = shareIntent(intent);
        if (shared == null) return;
        synchronized (this) {
            pendingIntent = shared;
        }
        notifyListeners(EVENT_SHARE_AVAILABLE, new JSObject(), true);
    }

    @PluginMethod
    public void consumeSharedImages(PluginCall call) {
        Intent intent;
        synchronized (this) {
            intent = pendingIntent;
            pendingIntent = null;
        }
        if (intent == null) {
            call.resolve(emptyResult());
            return;
        }

        getActivity().setIntent(new Intent(Intent.ACTION_MAIN));
        IO_EXECUTOR.execute(() -> {
            try {
                call.resolve(copySharedImages(intent));
            } catch (Exception exception) {
                call.reject("无法读取系统分享的图片", exception);
            }
        });
    }

    @PluginMethod
    public void releaseSharedImages(PluginCall call) {
        JSArray uris = call.getArray("uris", new JSArray());
        File directory = shareCacheDirectory();
        try {
            String root = directory.getCanonicalPath() + File.separator;
            for (Object value : uris.toList()) {
                String path = Uri.parse(String.valueOf(value)).getPath();
                if (path == null) continue;
                File file = new File(path).getCanonicalFile();
                if (file.getPath().startsWith(root)) file.delete();
            }
            call.resolve();
        } catch (IOException | JSONException | RuntimeException exception) {
            call.reject("无法清理分享图片", exception);
        }
    }

    private Intent shareIntent(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        String type = intent.getType();
        if ((!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action))
                || type == null || !type.startsWith("image/")) {
            return null;
        }
        return intent;
    }

    private JSObject copySharedImages(Intent intent) throws IOException {
        cleanupExpiredFiles();
        List<Uri> uris = sharedUris(intent);
        JSArray files = new JSArray();
        int rejected = 0;
        for (Uri uri : uris.subList(0, Math.min(MAX_FILES, uris.size()))) {
            try {
                JSObject copied = copyImage(uri);
                if (copied == null) rejected++;
                else files.put(copied);
            } catch (IOException | RuntimeException exception) {
                rejected++;
                Log.w(TAG, "A shared image could not be imported: " + exception.getClass().getSimpleName());
            }
        }
        if (uris.size() > MAX_FILES) rejected += uris.size() - MAX_FILES;

        JSObject result = new JSObject();
        result.put("files", files);
        result.put("rejected", rejected);
        return result;
    }

    private List<Uri> sharedUris(Intent intent) {
        Set<Uri> uris = new LinkedHashSet<>();
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null) uris.add(uri);
            }
        }

        ArrayList<Uri> streams = sharedStreamList(intent);
        if (streams != null) uris.addAll(streams);
        Uri stream = sharedStream(intent);
        if (stream != null) uris.add(stream);
        return new ArrayList<>(uris);
    }

    @SuppressWarnings("deprecation")
    private ArrayList<Uri> sharedStreamList(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    private Uri sharedStream(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private JSObject copyImage(Uri uri) throws IOException {
        ContentResolver resolver = getContext().getContentResolver();
        String originalName = displayName(resolver, uri, "shared-image");
        String contentType = normalizeType(resolver.getType(uri));
        if (contentType.isEmpty() || "image/*".equals(contentType)) {
            contentType = typeFromName(originalName);
        }

        String extension = extensionFor(contentType);
        if (extension != null) {
            return copySupportedImage(resolver, uri, originalName, contentType, extension);
        }
        if (!contentType.startsWith("image/")) return null;
        return transcodeImage(resolver, uri, originalName);
    }

    private JSObject copySupportedImage(ContentResolver resolver, Uri uri, String name,
                                        String contentType, String extension) throws IOException {
        File output = cacheFile(extension);
        long total;
        try {
            total = copyBounded(resolver, uri, output);
        } catch (IOException | RuntimeException exception) {
            output.delete();
            throw exception;
        }
        if (total <= 0) {
            output.delete();
            return null;
        }
        return fileResult(output, name, contentType, total);
    }

    private JSObject transcodeImage(ContentResolver resolver, Uri uri, String name) throws IOException {
        File source = cacheFile(".source");
        File output = cacheFile(".jpg");
        Bitmap bitmap = null;
        boolean keepOutput = false;
        try {
            if (copyBounded(resolver, uri, source) <= 0) return null;
            bitmap = decodeBoundedBitmap(source);
            if (bitmap == null) return null;
            try (FileOutputStream target = new FileOutputStream(output)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, target)) return null;
            }
            long size = output.length();
            if (size <= 0 || size > MAX_FILE_BYTES) return null;
            JSObject result = fileResult(output, replaceExtension(name, ".jpg"), "image/jpeg", size);
            keepOutput = true;
            return result;
        } finally {
            source.delete();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (!keepOutput) output.delete();
        }
    }

    private long copyBounded(ContentResolver resolver, Uri uri, File output) throws IOException {
        long total = 0;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return -1;
            try (FileOutputStream target = new FileOutputStream(output)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) continue;
                    total += read;
                    if (total > MAX_FILE_BYTES) return -1;
                    target.write(buffer, 0, read);
                }
            }
        }
        if (total > MAX_FILE_BYTES) return -1;
        return total;
    }

    private Bitmap decodeBoundedBitmap(File source) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeWithImageDecoder(source);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / (options.inSampleSize * 2) >= MAX_IMAGE_DIMENSION) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        return scaleDown(decoded);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private Bitmap decodeWithImageDecoder(File source) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source), (decoder, info, ignored) -> {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int longest = Math.max(width, height);
            if (longest > MAX_IMAGE_DIMENSION) {
                double scale = MAX_IMAGE_DIMENSION / (double) longest;
                decoder.setTargetSize(
                        Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(height * scale))
                );
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
        });
    }

    private Bitmap scaleDown(Bitmap bitmap) {
        if (bitmap == null) return null;
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= MAX_IMAGE_DIMENSION) return bitmap;
        double scale = MAX_IMAGE_DIMENSION / (double) longest;
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, (int) Math.round(bitmap.getWidth() * scale)),
                Math.max(1, (int) Math.round(bitmap.getHeight() * scale)),
                true
        );
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private File cacheFile(String extension) throws IOException {
        File directory = shareCacheDirectory();
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Unable to create share cache");
        return new File(directory, UUID.randomUUID().toString().replace("-", "") + extension);
    }

    private JSObject fileResult(File output, String name, String contentType, long size) {
        JSObject file = new JSObject();
        file.put("uri", Uri.fromFile(output).toString());
        file.put("name", name);
        file.put("type", contentType);
        file.put("size", size);
        file.put("lastModified", System.currentTimeMillis());
        return file;
    }

    private String displayName(ContentResolver resolver, Uri uri, String fallback) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && !value.isBlank()) return value;
                }
            }
        } catch (RuntimeException ignored) {
            // The generated cache name remains a safe fallback.
        }
        return fallback;
    }

    private String normalizeType(String type) {
        if (type == null) return "";
        String normalized = type.toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        if (separator >= 0) normalized = normalized.substring(0, separator);
        normalized = normalized.trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String typeFromName(String name) {
        String normalized = String.valueOf(name).toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".gif")) return "image/gif";
        if (normalized.endsWith(".webp")) return "image/webp";
        if (normalized.endsWith(".heic")) return "image/heic";
        if (normalized.endsWith(".heif")) return "image/heif";
        if (normalized.endsWith(".avif")) return "image/avif";
        return "";
    }

    private String replaceExtension(String name, String extension) {
        String value = name == null || name.isBlank() ? "shared-image" : name.trim();
        int separator = value.lastIndexOf('.');
        if (separator > 0) value = value.substring(0, separator);
        return value + extension;
    }

    private String extensionFor(String type) {
        return switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> null;
        };
    }

    private File shareCacheDirectory() {
        return new File(getContext().getCacheDir(), "shared-images");
    }

    private void cleanupExpiredFiles() {
        File[] files = shareCacheDirectory().listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) file.delete();
        }
    }

    private JSObject emptyResult() {
        JSObject result = new JSObject();
        result.put("files", new JSArray());
        result.put("rejected", 0);
        return result;
    }
}
