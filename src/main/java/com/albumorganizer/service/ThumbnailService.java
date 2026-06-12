package com.albumorganizer.service;

import com.albumorganizer.model.MediaType;
import com.albumorganizer.util.AppDirs;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThumbnailService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);

    private static final int THUMBNAIL_SIZE = 160;
    private static final long MAX_CACHE_BYTES = 42L * 1024 * 1024;
    private static final Path CACHE_DIR = AppDirs.thumbnailsDir();

    private final LinkedHashMap<String, Path> lruOrder;
    private long currentCacheBytes;

    public ThumbnailService() {
        this.lruOrder = new LinkedHashMap<>(64, 0.75f, true);
        this.currentCacheBytes = 0;
        try {
            migrateOldThumbnailCache();
            Files.createDirectories(CACHE_DIR);
            loadExistingCache();
        } catch (IOException e) {
            logger.error("Failed to create thumbnail cache directory", e);
        }
    }

    private void migrateOldThumbnailCache() {
        Path oldDir = Paths.get(System.getProperty("user.home"), ".config", "album-organizer", "thumbnails");
        if (!Files.isDirectory(oldDir) || oldDir.equals(CACHE_DIR)) return;
        try {
            Files.createDirectories(CACHE_DIR);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(oldDir)) {
                for (Path src : stream) {
                    Path dest = CACHE_DIR.resolve(src.getFileName());
                    if (!Files.exists(dest)) Files.copy(src, dest);
                }
            }
            logger.info("Migrated thumbnail cache from {} to {}", oldDir, CACHE_DIR);
        } catch (IOException e) {
            logger.warn("Could not migrate thumbnail cache: {}", e.getMessage());
        }
    }

    private void loadExistingCache() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CACHE_DIR, "*.jpg")) {
            for (Path entry : stream) {
                long size = Files.size(entry);
                lruOrder.put(entry.getFileName().toString(), entry);
                currentCacheBytes += size;
            }
            logger.debug("Loaded {} cached thumbnails ({} bytes)", lruOrder.size(), currentCacheBytes);
        } catch (IOException e) {
            logger.debug("Failed to scan existing cache: {}", e.getMessage());
        }
    }

    public Image generateThumbnail(Path file, MediaType type) {
        Path cachedFile = getCachePath(file);
        String cacheKey = cachedFile.getFileName().toString();

        if (lruOrder.containsKey(cacheKey) && Files.exists(cachedFile)) {
            lruOrder.get(cacheKey);
            try {
                return new Image(cachedFile.toUri().toString(), THUMBNAIL_SIZE, THUMBNAIL_SIZE, true, true);
            } catch (Exception e) {
                logger.debug("Failed to load cached thumbnail, regenerating: {}", file.getFileName());
            }
        }

        BufferedImage image;
        if (type == MediaType.VIDEO) {
            image = extractVideoFrame(file);
        } else if (needsSpecialHandling(file, type)) {
            image = convertViaSips(file);
        } else {
            try {
                image = ImageIO.read(file.toFile());
            } catch (IOException e) {
                logger.debug("Failed to read image directly: {}", file.getFileName(), e);
                image = null;
            }
            // ImageIO returns null for formats it doesn't recognize (e.g. WebP with wrong extension)
            if (image == null) {
                image = convertViaSips(file);
            }
        }

        if (image == null) {
            return null;
        }

        BufferedImage thumbnail = scaleTo(image, THUMBNAIL_SIZE);
        saveThumbnailCache(thumbnail, cachedFile);

        return SwingFXUtils.toFXImage(thumbnail, null);
    }

    private BufferedImage extractVideoFrame(Path file) {
        BufferedImage result = tryExtractVideoFrame(file, false);
        if (result != null) {
            return result;
        }
        // Retry ignoring unknown audio codec parameters (e.g. apac/Apple Lossless)
        return tryExtractVideoFrame(file, true);
    }

    public boolean isAudioOnly(Path file) {
        try (FFmpegFrameGrabber probe = new FFmpegFrameGrabber(file.toFile())) {
            probe.setAudioStream(-1);
            probe.setVideoStream(0);
            probe.setOption("analyzeduration", "1000000");
            probe.setOption("probesize", "1000000");
            probe.setOption("err_detect", "ignore_err");
            try {
                probe.start();
            } catch (Exception e) {
                // If start fails, assume it's not audio-only (let video extraction attempt handle it)
                return false;
            }
            int width = probe.getImageWidth();
            probe.stop();
            return width <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private BufferedImage tryExtractVideoFrame(Path file, boolean ignoreAudio) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {

            grabber.setVideoStream(0);
            grabber.setAudioStream(-1);
            if (ignoreAudio) {
                // Use a permissive option to skip unknown audio codec parameters
                grabber.setOption("allowed_extensions", "ALL");
                grabber.setOption("err_detect", "ignore_err");
            }
            grabber.start();
            if (grabber.getImageWidth() <= 0) {
                grabber.stop();
                return null;
            }
            long duration = grabber.getLengthInTime();
            double displayRotation = grabber.getDisplayRotation();

            // Try multiple positions to avoid dark/blank frames
            long[] seekPositions;
            if (duration > 0) {
                seekPositions = new long[]{
                        duration / 10,
                        duration / 4,
                        duration / 3,
                        duration / 2
                };
            } else {
                seekPositions = new long[]{0};
            }

            for (long position : seekPositions) {
                grabber.setTimestamp(position);
                Frame frame = grabber.grabImage();
                if (frame == null) continue;

                BufferedImage image = converter.convert(frame);
                if (image != null && !isDark(image)) {
                    grabber.stop();
                    return applyRotation(image, displayRotation);
                }
            }

            // If all positions were dark, use the first one that produced an image
            grabber.setTimestamp(seekPositions[0]);
            Frame frame = grabber.grabImage();
            if (frame != null) {
                BufferedImage image = converter.convert(frame);
                grabber.stop();
                return applyRotation(image, displayRotation);
            }

            grabber.stop();
            return null;
        } catch (Exception e) {
            if (!ignoreAudio) {
                logger.debug("Failed to extract video frame from {} (will retry): {}", file.getFileName(), e.getMessage());
            } else {
                logger.debug("Failed to extract video frame from {}: {}", file.getFileName(), e.getMessage());
            }
            return null;
        }
    }

    private BufferedImage applyRotation(BufferedImage image, double displayRotation) {
        if (image == null || displayRotation == 0.0) return image;

        int angle = ((int) Math.round(-displayRotation) % 360 + 360) % 360;
        if (angle == 0) return image;

        int w = image.getWidth();
        int h = image.getHeight();
        int newW = (angle == 90 || angle == 270) ? h : w;
        int newH = (angle == 90 || angle == 270) ? w : h;

        BufferedImage rotated = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = rotated.createGraphics();
        g2d.translate(newW / 2.0, newH / 2.0);
        g2d.rotate(Math.toRadians(angle));
        g2d.translate(-w / 2.0, -h / 2.0);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    private boolean isDark(BufferedImage image) {
        int sampleSize = 100;
        int w = image.getWidth();
        int h = image.getHeight();
        long totalBrightness = 0;
        int samples = 0;

        for (int i = 0; i < sampleSize; i++) {
            int x = (int) (Math.random() * w);
            int y = (int) (Math.random() * h);
            int rgb = image.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            totalBrightness += (r + g + b) / 3;
            samples++;
        }

        double avgBrightness = (double) totalBrightness / samples;
        return avgBrightness < 30;
    }

    private BufferedImage convertViaSips(Path file) {
        // Use macOS sips to convert HEIC/WebP/etc. to JPEG
        Path tempJpeg = null;
        try {
            tempJpeg = Files.createTempFile("sips_thumb_", ".jpg");
            ProcessBuilder pb = new ProcessBuilder(
                    "sips", "-s", "format", "jpeg",
                    "-s", "formatOptions", "80",
                    file.toAbsolutePath().toString(),
                    "--out", tempJpeg.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0 && Files.exists(tempJpeg) && Files.size(tempJpeg) > 0) {
                return ImageIO.read(tempJpeg.toFile());
            } else {
                logger.debug("sips conversion failed for {}, exit code: {}", file.getFileName(), exitCode);
                return null;
            }
        } catch (Exception e) {
            logger.debug("Failed to convert {} via sips: {}", file.getFileName(), e.getMessage());
            return null;
        } finally {
            if (tempJpeg != null) {
                try {
                    Files.deleteIfExists(tempJpeg);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private BufferedImage scaleTo(BufferedImage source, int maxSize) {
        int w = source.getWidth();
        int h = source.getHeight();

        if (w <= maxSize && h <= maxSize) {
            return source;
        }

        double scale = Math.min((double) maxSize / w, (double) maxSize / h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(source, 0, 0, newW, newH, null);
        g2d.dispose();
        return scaled;
    }

    private void saveThumbnailCache(BufferedImage image, Path cachePath) {
        try {
            ImageIO.write(image, "JPEG", cachePath.toFile());
            long size = Files.size(cachePath);
            String cacheKey = cachePath.getFileName().toString();
            lruOrder.put(cacheKey, cachePath);
            currentCacheBytes += size;
            logger.debug("Cached thumbnail: {} ({} bytes)", cachePath.getFileName(), size);
            evictIfNeeded();
        } catch (IOException e) {
            logger.debug("Failed to cache thumbnail: {}", e.getMessage());
        }
    }

    private void evictIfNeeded() {
        while (currentCacheBytes > MAX_CACHE_BYTES && !lruOrder.isEmpty()) {
            Map.Entry<String, Path> eldest = lruOrder.entrySet().iterator().next();
            Path evictPath = eldest.getValue();
            try {
                long size = Files.exists(evictPath) ? Files.size(evictPath) : 0;
                Files.deleteIfExists(evictPath);
                currentCacheBytes -= size;
                logger.debug("Evicted LRU thumbnail: {}", eldest.getKey());
            } catch (IOException e) {
                logger.debug("Failed to evict thumbnail: {}", e.getMessage());
            }
            lruOrder.remove(eldest.getKey());
        }
    }

    public void clearCache() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CACHE_DIR, "*.jpg")) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException e) {
            logger.debug("Failed to clear thumbnail cache: {}", e.getMessage());
        }
        lruOrder.clear();
        currentCacheBytes = 0;
        logger.debug("Thumbnail cache cleared");
    }

    public static String archiveEntryCacheKey(String archivePath, String entryName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((archivePath + "#" + entryName).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf((archivePath + "#" + entryName).hashCode());
        }
    }

    private Path getCachePath(Path file) {
        String hash = sha1(file.toAbsolutePath().toString());
        return CACHE_DIR.resolve(hash + ".jpg");
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    /** Generates a thumbnail from raw image bytes (for archive entries). */
    public Image generateThumbnailFromBytes(byte[] bytes, String cacheKey) {
        Path cachedFile = CACHE_DIR.resolve(cacheKey + ".jpg");
        if (lruOrder.containsKey(cachedFile.getFileName().toString()) && Files.exists(cachedFile)) {
            lruOrder.get(cachedFile.getFileName().toString());
            try {
                return new Image(cachedFile.toUri().toString(), THUMBNAIL_SIZE, THUMBNAIL_SIZE, true, true);
            } catch (Exception ignored) {}
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) return null;
            BufferedImage thumbnail = scaleTo(src, THUMBNAIL_SIZE);
            saveThumbnailCache(thumbnail, cachedFile);
            return SwingFXUtils.toFXImage(thumbnail, null);
        } catch (Exception e) {
            logger.debug("Failed to generate thumbnail from bytes: {}", e.getMessage());
            return null;
        }
    }

    public boolean needsSpecialHandling(Path file, MediaType type) {
        if (type == MediaType.VIDEO) {
            return true;
        }
        String name = file.getFileName().toString().toLowerCase();
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (isMac) {
            return name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".webp");
        }
        return name.endsWith(".webp");
    }
}
